package sorra.answerer.central;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jdt.core.dom.*;
import sorra.answerer.ast.*;
import sorra.answerer.io.FileUtil;
import sorra.answerer.io.FileWalker;
import sorra.answerer.util.EventSeq;
import sorra.answerer.util.PrimitiveUtil;
import sorra.answerer.util.StringUtil;

public class DoWire {

  public static void run(String javaFolder) {
    Supplier<Collection<File>> findAll = () -> FileWalker.findAll(new Path[]{Paths.get(javaFolder)},
        path -> {
          String p = path.toString();
          return p.endsWith(".java") && !p.equals("package-info.java");
        });
    FileWalker.walkAll(findAll.get(), fileAction(ctx -> ConfigReader.read(ctx.cu)), 1);
    FileWalker.walkAll(findAll.get(), fileAction(DoWire::processEnableRest), 1);
    FileWalker.walkAll(findAll.get(), fileAction(DoWire::processUserFunction), 1);
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

      List<AutowireResult> results = new ArrayList<>();
      atd.bodyDeclarations().stream().forEach(bodyDecl -> {
        if (bodyDecl instanceof MethodDeclaration) {
          ((MethodDeclaration) bodyDecl).getBody().accept(autowireVisitor(ctx, eventSeq, results));
        }
      });

      if (ctx.modified) {
        try {
          FileUtils.write(ctx.file, eventSeq.run(), StandardCharsets.UTF_8);
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }
    }
  }

  private static ASTVisitor autowireVisitor(AstContext ctx, EventSeq eventSeq, List<AutowireResult> results) {
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
            opsWriter.writeLine(String.format("%s<%s> %s = new %s();",
                toCollTypeName, toTypeQname, returnVar, "java.util."+javaCollections.get(toCollTypeName)+"<>"));
            SimpleName fromVarSimpleName = (SimpleName) mi.arguments().get(0);
            String fromVarName = fromVarSimpleName.getIdentifier();
            String fromTypeQname = new VariableTypeResolver(fromVarSimpleName).resolveTypeQname();
            if (!fromTypeQname.contains("<")) {
              throw new RuntimeException("autowire is in collection mode, requires collection argument!");
            }
            String elemQname = AstFind.qnameOfTypeRef(StringUtils.substringBetween(fromTypeQname, "<", ">"), ctx.cu);
            opsWriter.writeLine(String.format("for (%s %s : %s) {",
                elemQname, "each", fromVarName));
            opsWriter.setIndent(3);
          } else {
            opsWriter.setIndent(2);
          }
          opsWriter.writeLine(String.format("%s %s = new %s();", toTypeQname, toVarName, toTypeQname));

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
              String fromTypeQname = new VariableTypeResolver(fromVarSimpleName).resolveTypeQname();
              paramTypes.add(fromTypeQname);
              params.add(fromTypeQname + " " + fromVarName);

              if (toCollTypeName == null) {
                Optional<String> varLine = SingleVariableCopier.getLine(fromVarName, fromTypeQname, toVarName, toFields);
                if (varLine.isPresent()) {
                  opsWriter.writeLine(varLine.get());
                } else {
                  ObjectPropsCopier.get(fromVarName, fromTypeQname, toVarName, toTypeQname, toFields)
                      .getLines().forEach(opsWriter::writeLine);
                }
              } else {
                String elemQname = AstFind.qnameOfTypeRef(StringUtils.substringBetween(fromTypeQname, "<", ">"), ctx.cu);
                ObjectPropsCopier.get("each", elemQname, toVarName, toTypeQname, toFields)
                    .getLines().forEach(opsWriter::writeLine);
              }
            } else {
              if (idxVarAmend+1 != i) throw new RuntimeException("Var amending is not followed by var expression!");
              String fromVarName = varAmend.substring(0, varAmend.length()-1);

              Optional<VariableDeclarationFragment> matchField = toFields.stream()
                  .filter(vdFrag -> vdFrag.getName().getIdentifier().equals(fromVarName)).findFirst();
              if (!matchField.isPresent()) {
                throw new RuntimeException(String.format("Bad var amending '%s' for object '%s'", fromVarName, toVarName));
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
            opsWriter.writeLine(String.format("%s.add(%s);", returnVar, toVarName));
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
        String methodSig = wireMethodName + " " + String.join(", ", paramTypes);
        String methodFullName = wireMethodName + " " + String.join(", ", params);
        boolean existsFullName = existsFullName(methodFullName);

        // Deduplicate autowire methods
        if (!existsFullName && existsSig(methodSig)) {
          String candidateMethodName, candidateMethodSig;
          int i = 0;
          do {
            i++;
            candidateMethodName = wireMethodName + i;
            candidateMethodSig = candidateMethodName + " " + String.join(", ", paramTypes);
          } while (existsSig(candidateMethodSig));
          wireMethodName = candidateMethodName;
          methodSig = candidateMethodSig;
        }
        results.add(new AutowireResult(wireMethodName, methodSig, methodFullName));

        // Modify the call place
        SimpleName methName = mi.getName();
        eventSeq.add(new EventSeq.Insertion(wireMethodName, methName.getStartPosition()));
        eventSeq.add(new EventSeq.Deletion(
            methName.getStartPosition(), methName.getStartPosition() + methName.getLength()));
        eventSeq.add(new EventSeq.Deletion(mi.getExpression().getStartPosition(),
            mi.getExpression().getStartPosition()+mi.getExpression().getLength()+1));
        for (Expression rmArg : removedArgs) {
          int begin = rmArg.getStartPosition();
          int nextComma = ctx.source.indexOf(',', rmArg.getStartPosition() + rmArg.getLength());
          if (nextComma <= 0) throw new RuntimeException("Wrong comma position after var amending arg!");
          int end = ctx.source.charAt(nextComma+1) == ' ' ? nextComma+2 : nextComma+1;
          eventSeq.add(new EventSeq.Deletion(begin, end));
        }

        if (!existsFullName) { // Create an autowire method
          PartWriter wireMethodWriter = new PartWriter();
          wireMethodWriter.setIndent(1);
          wireMethodWriter.writeLine(String.format("public static %s %s(%s) {",
              returnType, wireMethodName, String.join(", ", params)));
          wireMethodWriter.setIndent(0);
          opsWriter.getLines().forEach(wireMethodWriter::writeLine);
          wireMethodWriter.setIndent(2);
          wireMethodWriter.writeLine(String.format("return %s;", returnVar));
          wireMethodWriter.setIndent(1);
          wireMethodWriter.writeLine("}\n");
          AbstractTypeDeclaration atd = FindUpper.abstractTypeScope(mi);
          assert atd != null;
          eventSeq.add(new EventSeq.Insertion(String.join("\n", wireMethodWriter.getLines()),
              atd.getStartPosition() + atd.getLength() - 2));
        }
      }

      private boolean existsFullName(String methodFullName) {
        return results.stream().anyMatch(result -> result.methodFullName.equals(methodFullName));
      }

      private boolean existsSig(String methodSig) {
        return results.stream().anyMatch(result -> result.methodSig.equals(methodSig));
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

  static class AutowireResult {
    final String methodName;
    final String methodSig;
    final String methodFullName;

    public AutowireResult(String methodName, String methodSig, String methodFullName) {
      this.methodName = methodName;
      this.methodSig = methodSig;
      this.methodFullName = methodFullName;
    }
  }

  private static Map<String, String> javaCollections = new HashMap<>();
  static {
    javaCollections.put("Collection", "ArrayList");
    javaCollections.put("List", "ArrayList");
    javaCollections.put("Set", "HashSet");
  }
}
