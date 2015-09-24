package sorra.answerer.central;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jdt.core.dom.*;
import sorra.answerer.ast.*;
import sorra.answerer.io.FileUtil;
import sorra.answerer.io.FileWalker;
import sorra.answerer.util.EventSeq;
import sorra.answerer.util.PrimitiveUtil;
import sorra.answerer.util.StringUtil;

import static java.lang.String.format;

public class DoWire {

  private static Map<String, List<AutowireMethod>> pkgs2Wirers = new ConcurrentHashMap<>();

  public static void run(String javaFolder) {
    Supplier<Collection<File>> findAll = () -> FileWalker.findAll(new Path[]{Paths.get(javaFolder)},
        path -> {
          String p = path.toString();
          return p.endsWith(".java") && !p.equals("package-info.java");
        });
    FileWalker.walkAll(findAll.get(), fileAction(ctx -> ConfigReader.read(ctx.cu)), 1);
    FileWalker.walkAll(findAll.get(), fileAction(DoWire::processEnableRest), 1);
    FileWalker.walkAll(findAll.get(), fileAction(DoWire::processUserFunction), 1);
    pkgs2Wirers.forEach((pkg, methods) -> {
      PartWriter wirer = new PartWriter();
      wirer.writeLine(format("package %s;\n", pkg));
      wirer.writeLine("import java.util.Collection;");
      wirer.writeLine("import java.util.List;");
      wirer.writeLine("import java.util.Set;");
      wirer.writeLine("\nclass Wirer {");
      methods.forEach(method -> method.code.getLines().forEach(wirer::writeLine));
      wirer.writeLine("}");
      try {
        FileUtils.write(new File(javaFolder+"/"+pkg.replace('.', '/')+"/Wirer.java"),
            String.join("\n", wirer.getLines()), StandardCharsets.UTF_8);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    });
  }

  private static Consumer<File> fileAction(Consumer<AstContext> consumer) {
    return file -> {
      try {
        String source = FileUtil.readFile(file.getPath());
        CompilationUnit cu = Parser.parse(source);
        consumer.accept(new AstContext(file, source, cu));
      } catch (Throwable e) {
        System.err.println("Error at: " + file.getPath());
        e.printStackTrace();
      }
    };
  }

  static void processUserFunction(AstContext ctx) {
    AbstractTypeDeclaration atd = (AbstractTypeDeclaration) ctx.cu.types().get(0);
    if (atd.getJavadoc() != null && atd.getJavadoc().toString().contains("$UserFunction")) {
      EventSeq eventSeq = new EventSeq(ctx.source);
      ImportDeclaration wirerImport = AstFind.findImport("sorra.answerer.api.Wirer", ctx.cu.imports());
      if (wirerImport != null) {
        eventSeq.add(new EventSeq.Deletion(wirerImport.getStartPosition(), wirerImport.getStartPosition()+wirerImport.getLength()));
      }

      String pkgName = ctx.cu.getPackage().getName().getFullyQualifiedName();
      // Concurrent!
      List<AutowireMethod> wirer = pkgs2Wirers.putIfAbsent(pkgName, new ArrayList<>());
      if (wirer == null) {
        wirer = pkgs2Wirers.get(pkgName);
      }
      List<AutowireMethod> wireMethods = wirer;

      atd.bodyDeclarations().stream().forEach(bodyDecl -> {
        if (bodyDecl instanceof MethodDeclaration) {
          ((MethodDeclaration) bodyDecl).getBody().accept(autowireVisitor(ctx, eventSeq, wireMethods));
        }
      });

      if (ctx.modified) {
        try {
          FileUtils.write(ctx.file, eventSeq.run(), StandardCharsets.UTF_8);
          System.out.println("* Modified file: " + ctx.file.getPath());
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }
    }
  }

  private static ASTVisitor autowireVisitor(AstContext ctx, EventSeq eventSeq, List<AutowireMethod> wireMethods) {
    return new ASTVisitor() {
      @Override
      public boolean visit(MethodInvocation mi) {
        if (mi.getName().getIdentifier().equals("autowire")
            && mi.getExpression() != null && mi.getExpression().toString().trim().equals("Wirer")) {
          ctx.modified = true;
          Statement statement = FindUpper.statement(mi);
          Type toTypeRef;
          String toVarName;
          if (statement instanceof ReturnStatement && mi == ((ReturnStatement) statement).getExpression()) {
            MethodDeclaration md = FindUpper.methodScope(statement);
            assert md != null;
            toTypeRef = md.getReturnType2();
            toVarName = StringUtils.uncapitalize(AstFind.snameOfTypeRef(getElementalType(toTypeRef)));
          } else if (statement instanceof ExpressionStatement) {
            VariableDeclarationExpression decl = (VariableDeclarationExpression)
                ((ExpressionStatement)statement).getExpression();
            toTypeRef = decl.getType();
            toVarName = ((VariableDeclarationFragment)decl.fragments().get(0)).getName().getIdentifier();
          } else if (statement instanceof VariableDeclarationStatement) {
            VariableDeclarationStatement vds = (VariableDeclarationStatement) statement;
            toTypeRef = vds.getType();
            toVarName = ((VariableDeclarationFragment)vds.fragments().get(0)).getName().getIdentifier();
          } else {
            throw new RuntimeException("Must declare a target variable. Statement: "+statement);
          }

          String toCollTypeName = null;
          if (toTypeRef.isParameterizedType()) {
            ParameterizedType genericType = (ParameterizedType) toTypeRef;
            String rawTypeName = StringUtil.simpleName(genericType.getType().toString().trim());
            if (javaCollections.containsKey(rawTypeName)) {
              toCollTypeName = rawTypeName;
            }
          }

          toVarName = "_"+toVarName;
          String toTypeQname = AstFind.qnameOfTypeRef(getElementalType(toTypeRef));
          String returnVar = toCollTypeName==null ? toVarName : toVarName+"s";

          List<Expression> removedArgs = new ArrayList<>();
          List<String> paramTypes = new ArrayList<>();
          List<String> params = new ArrayList<>();
          PartWriter opsWriter = new PartWriter();

          if (toCollTypeName != null) {
            opsWriter.setIndent(2);
            opsWriter.writeLine(format("%s<%s> %s = new %s();",
                toCollTypeName, toTypeQname, returnVar, "java.util." + javaCollections.get(toCollTypeName) + "<>"));
            SimpleName fromVarSimpleName = (SimpleName) mi.arguments().get(0);
            String fromVarName = fromVarSimpleName.getIdentifier();
            String fromTypeQname = new VariableTypeResolver(fromVarSimpleName).resolveTypeQname();
            if (!fromTypeQname.contains("<")) {
              throw new RuntimeException("autowire is in collection mode, requires collection argument!");
            }
            String elemQname = AstFind.qnameOfTypeRef(StringUtils.substringBetween(fromTypeQname, "<", ">"), ctx.cu);
            opsWriter.writeLine(format("for (%s %s : %s) {",
                elemQname, "each", fromVarName));
            opsWriter.setIndent(3);
          } else {
            opsWriter.setIndent(2);
          }
          opsWriter.writeLine(format("%s %s = new %s();", toTypeQname, toVarName, toTypeQname));

          TypeDeclaration toTypeDecl = (TypeDeclaration) Sources.getCuByQname(toTypeQname).types().get(0);
          List<VariableDeclarationFragment> toFields = AstFind.fields(toTypeDecl);
          List<Expression> args = mi.arguments();
          int idxVarAmend = -1;
          String varAmend = null;
          for (int i = 0; i < args.size(); i++) {
            Expression cur = args.get(i);
            if (cur instanceof StringLiteral) {
              String strLiteral = ((StringLiteral) cur).getLiteralValue();
              if (strLiteral.endsWith("=")) {
                removedArgs.add(cur);
                idxVarAmend = i; varAmend = strLiteral;
                if (i == args.size()) throw new RuntimeException("Var amending cannot be the last arg");
                continue;
              }
            }

            if (varAmend == null) {
              if (cur instanceof SimpleName == false) {
                throw new RuntimeException("Var must be a SimpleName when without amending");
              }
              SimpleName fromVarSimpleName = (SimpleName) cur;
              String fromVarName = fromVarSimpleName.getIdentifier();
              Type fromType = new VariableTypeResolver(fromVarSimpleName).resolveType();
              String fromTypeQname = complexQname(fromType);
              paramTypes.add(fromTypeQname);
              params.add(fromTypeQname + " " + fromVarName);

              if (toCollTypeName == null) {
                Optional<String> varLine = SingleVariableCopier.getLine(fromVarName, fromTypeQname, toVarName, toFields);
                if (varLine.isPresent()) {
                  opsWriter.writeLine(varLine.get());
                } else {
                  ObjectPropsCopier.get(fromVarName, fromTypeQname, toVarName, toTypeQname, toFields, wireMethods)
                      .getLines().forEach(opsWriter::writeLine);
                }
              } else {
                String elemQname = AstFind.qnameOfTypeRef(StringUtils.substringBetween(fromTypeQname, "<", ">"), ctx.cu);
                ObjectPropsCopier.get("each", elemQname, toVarName, toTypeQname, toFields, wireMethods)
                    .getLines().forEach(opsWriter::writeLine);
              }
            } else {
              if (idxVarAmend+1 != i) throw new RuntimeException("Var amending is not followed by var expression!");
              String fromVarName = varAmend.substring(0, varAmend.length()-1);

              Optional<VariableDeclarationFragment> matchField = toFields.stream()
                  .filter(vdFrag -> vdFrag.getName().getIdentifier().equals(fromVarName)).findFirst();
              if (!matchField.isPresent()) {
                throw new RuntimeException(format("Bad var amending '%s' for object '%s'", fromVarName, toVarName));
              }
              Type type = ((FieldDeclaration) matchField.get().getParent()).getType();
              String fieldTypeQname = AstFind.qnameOfTypeRef(type);

              //Box primitive params, to escape from NPE
              String boxType = PrimitiveUtil.boxType(fieldTypeQname);
              paramTypes.add(boxType);
              params.add(boxType + " " + fromVarName);

              String varLine = SingleVariableCopier.getLine(fromVarName, cur, toVarName, toFields);
              opsWriter.writeLine(varLine);
              idxVarAmend = -1; varAmend = null;
            }
          }

          if (toCollTypeName != null) {
            opsWriter.writeLine(format("%s.add(%s);", returnVar, toVarName));
            opsWriter.setIndent(2);
            opsWriter.writeLine("}");
          }

          String returnType = toCollTypeName==null ? toTypeQname : toCollTypeName+"<"+toTypeQname+">";
          genAutowireMethod(mi, returnType, returnVar, removedArgs, paramTypes, params, opsWriter);
        }
        return true;
      }

      private void genAutowireMethod(MethodInvocation mi, String returnType, String returnVar,
                                     List<Expression> removedArgs, List<String> paramTypes, List<String> params, PartWriter opsWriter) {
        if (paramTypes.size() != params.size()) throw new RuntimeException();

        String wireMethodName = "autowire"+ StringUtils.capitalize(StringUtils.removeStart(returnVar, "_"));
        boolean exists = exists(new AutowireMethod(wireMethodName, params, null));

        if (!exists) {
          String newMethodName = wireMethodName;
          int i = 2;
          while (existName(newMethodName)) {
            newMethodName = wireMethodName + i;
            i++;
          }
          wireMethodName = newMethodName;
        }

        // Modify the call place
        SimpleName methName = mi.getName();
        eventSeq.add(new EventSeq.Insertion(wireMethodName, methName.getStartPosition()));
        eventSeq.add(new EventSeq.Deletion(
            methName.getStartPosition(), methName.getStartPosition() + methName.getLength()));
//        eventSeq.add(new EventSeq.Deletion(mi.getExpression().getStartPosition(),
//            mi.getExpression().getStartPosition()+mi.getExpression().getLength()+1));
        for (Expression rmArg : removedArgs) {
          int begin = rmArg.getStartPosition();
          int nextComma = ctx.source.indexOf(',', rmArg.getStartPosition() + rmArg.getLength());
          if (nextComma <= 0) throw new RuntimeException("Wrong comma position after var amending arg!");
          int end = ctx.source.charAt(nextComma+1) == ' ' ? nextComma+2 : nextComma+1;
          eventSeq.add(new EventSeq.Deletion(begin, end));
        }

        if (exists) return;
        // Generate the autowire method
        PartWriter wireMethodWriter = new PartWriter();
        wireMethodWriter.setIndent(1);
        wireMethodWriter.writeLine(format("public static %s %s(%s) {",
            returnType, wireMethodName, String.join(", ", params)));
        wireMethodWriter.setIndent(0);
        opsWriter.getLines().forEach(wireMethodWriter::writeLine);
        wireMethodWriter.setIndent(2);
        wireMethodWriter.writeLine(format("return %s;", returnVar));
        wireMethodWriter.setIndent(1);
        wireMethodWriter.writeLine("}\n");
//        AbstractTypeDeclaration atd = FindUpper.abstractTypeScope(mi);
//        assert atd != null;
//        eventSeq.add(new EventSeq.Insertion(String.join("\n", wireMethodWriter.getLines()),
//            atd.getStartPosition() + atd.getLength() - 2));
        wireMethods.add(new AutowireMethod(wireMethodName, params, wireMethodWriter));
      }

      private boolean existName(String name) {
        return wireMethods.stream().anyMatch(each -> each.name.equals(name));
      }

      private boolean exists(AutowireMethod other) {
        return wireMethods.stream().anyMatch(
            each -> each.name.equals(other.name) && each.params.equals(other.params));
      }
    };
  }

  private static Type getElementalType(Type toTypeRef) {
    if (toTypeRef instanceof ParameterizedType) {
      return  (Type) ((ParameterizedType) toTypeRef).typeArguments().get(0);
    } else if (toTypeRef instanceof ArrayType) {
      throw new RuntimeException("Array is not supported by autowire");
    } else {
      return toTypeRef;
    }
  }

  static void processEnableRest(AstContext ctx) {
    AbstractTypeDeclaration atd = (AbstractTypeDeclaration) ctx.cu.types().get(0);
    if (atd.getJavadoc() != null && atd.getJavadoc().toString().contains("$EnableRest")) {
      if (atd instanceof TypeDeclaration == false) {
        throw new RuntimeException("EnableRest only supports 'class'!");
      }
      boolean isEntity = atd.modifiers().stream().anyMatch(mod -> {
        if (mod instanceof Annotation) {
          Annotation anno = (Annotation) mod;
          String annoQname = AstFind.qnameOfTypeRef(anno.getTypeName().getFullyQualifiedName(), ctx.cu);
          return annoQname.equals("javax.persistence.Entity");
        } else return false;
      });

      String xxxQname = AstFind.qnameOfTopTypeDecl(atd.getName());
      String entityQname = isEntity ? xxxQname : Relations.findEntity(xxxQname);
      if (entityQname == null) {
        throw new RuntimeException("Entity Qname not found for: " + xxxQname);
      }
      ProjectGenerator.newController(entityQname, xxxQname, StringUtils.uncapitalize(StringUtil.simpleName(xxxQname)));
    }
  }

  private static String complexQname(Type type) {
    if (type.isParameterizedType()) {
      ParameterizedType ptype = (ParameterizedType) type;
      Iterator<String> qargs = ptype.typeArguments().stream().map(typeArg -> AstFind.qnameOfTypeRef(((Type) typeArg))).iterator();
      return ptype.getType().toString().trim()+"<"+StringUtils.join(qargs, ", ")+">";
    } else {
      return AstFind.qnameOfTypeRef(type);
    }
  }

  static class AutowireMethod {
    final String name;
    final List<String> params;
    final PartWriter code;

    public AutowireMethod(String name, List<String> params, PartWriter code) {
      this.name = name;
      this.params = params;
      this.code = code;
    }
  }

  private static Map<String, String> javaCollections = new HashMap<>();
  static {
    javaCollections.put("Collection", "ArrayList");
    javaCollections.put("List", "ArrayList");
    javaCollections.put("Set", "HashSet");
  }
}
