package sorra.answerer.central;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.core.dom.*;
import sorra.answerer.ast.AstContext;
import sorra.answerer.ast.AstFind;
import sorra.answerer.ast.FindUpper;
import sorra.answerer.ast.VariableTypeResolver;
import sorra.answerer.util.EventSeq;
import sorra.answerer.util.PrimitiveUtil;
import sorra.answerer.util.StringUtil;

import static java.lang.String.format;

class Autowire {
  private static Map<String, List<AutowireMethod>> pkgs2Wirers = new ConcurrentHashMap<>();

  static List<AutowireMethod> getMethods(String pkg) {
    List<AutowireMethod> wirer = pkgs2Wirers.putIfAbsent(pkg, Collections.synchronizedList(new ArrayList<>()));
    if (wirer == null) {
      wirer = pkgs2Wirers.get(pkg);
    }
    return wirer;
  }

  static void writeWirers(String javaFolder) {
    pkgs2Wirers.forEach((pkg, methods) -> {
      if (methods.isEmpty()) return;
      PartWriter wirer = new PartWriter();
      wirer.writeLine(format("package %s;\n", pkg));
      wirer.writeLine("import java.util.Collection;");
      wirer.writeLine("import java.util.List;");
      wirer.writeLine("import java.util.Set;");
      wirer.writeLine("\nclass Wirer {");
      methods.forEach(method -> method.code.getLines().forEach(wirer::writeLine));
      wirer.writeLine("}");
      try {
        FileUtils.write(new File(javaFolder + "/" + pkg.replace('.', '/') + "/Wirer.java"),
            wirer.getWhole(), StandardCharsets.UTF_8);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    });
  }

  static ASTVisitor visitor(AstContext ctx, EventSeq eventSeq, List<AutowireMethod> wireMethods) {
    return new ASTVisitor() {

      @Override
      public boolean visit(MethodInvocation mi) {
        if (mi.getName().getIdentifier().equals("autowire")
            && mi.getExpression() != null && mi.getExpression().toString().trim().equals("Wirer")) {
          operate(mi);
        }
        return true;
      }

      void operate(MethodInvocation mi) {
        ctx.modified = true;
        Statement statement = FindUpper.statement(mi);
        Type toTypeRef;
        String _toVarName;
        if (statement instanceof ReturnStatement && mi == ((ReturnStatement) statement).getExpression()) {
          MethodDeclaration md = FindUpper.methodScope(statement);
          assert md != null;
          toTypeRef = md.getReturnType2();
          _toVarName = StringUtils.uncapitalize(AstFind.snameOfTypeRef(getElementalType(toTypeRef)));
        } else if (statement instanceof ExpressionStatement) {
          VariableDeclarationExpression decl = (VariableDeclarationExpression)
              ((ExpressionStatement)statement).getExpression();
          toTypeRef = decl.getType();
          _toVarName = ((VariableDeclarationFragment)decl.fragments().get(0)).getName().getIdentifier();
        } else if (statement instanceof VariableDeclarationStatement) {
          VariableDeclarationStatement vds = (VariableDeclarationStatement) statement;
          toTypeRef = vds.getType();
          _toVarName = ((VariableDeclarationFragment)vds.fragments().get(0)).getName().getIdentifier();
        } else {
          throw new RuntimeException("Must declare a target variable. Statement: "+statement);
        }

        String _toCollTypeName = null;
        if (toTypeRef.isParameterizedType()) {
          ParameterizedType genericType = (ParameterizedType) toTypeRef;
          String rawTypeName = StringUtil.simpleName(genericType.getType().toString().trim());
          if (javaCollections.containsKey(rawTypeName)) {
            _toCollTypeName = rawTypeName;
          }
        }
        String toCollTypeName = _toCollTypeName;

        String toVarName = "_"+_toVarName;
        String toTypeQname = AstFind.qnameOfTypeRef(getElementalType(toTypeRef));
        String returnVar = toCollTypeName==null ? toVarName : toVarName+"s";

        List<Expression> removedArgs = new ArrayList<>();
        List<String> paramTypes = new ArrayList<>();
        List<String> params = new ArrayList<>();
        PartWriter bodyWriter = new PartWriter();

        if (toCollTypeName != null) {
          bodyWriter.setIndent(2);
          bodyWriter.writeLine(format("%s<%s> %s = new %s();",
              toCollTypeName, toTypeQname, returnVar, "java.util." + javaCollections.get(toCollTypeName) + "<>"));
          SimpleName firstArg = (SimpleName) mi.arguments().get(0);
          String fromVarName = firstArg.getIdentifier();
          String fromTypeQname = new VariableTypeResolver(firstArg).resolveTypeQname();
          if (!fromTypeQname.contains("<")) {
            throw new RuntimeException("autowire is in collection mode, requires collection argument!");
          }
          String elemQname = AstFind.qnameOfTypeRef(StringUtils.substringBetween(fromTypeQname, "<", ">"), ctx.cu);
          bodyWriter.writeLine(format("for (%s %s : %s) {",
              elemQname, "each", fromVarName));
          bodyWriter.setIndent(3);
        } else {
          bodyWriter.setIndent(2);
        }
        bodyWriter.writeLine(format("%s %s = new %s();", toTypeQname, toVarName, toTypeQname));

        TypeDeclaration toTypeDecl = (TypeDeclaration) Sources.getCuByQname(toTypeQname).types().get(0);
        List<VariableDeclarationFragment> toFields = AstFind.fields(toTypeDecl);

        List<Expression> args = mi.arguments();
        List<Consumer<PartWriter>> operations = new ArrayList<>();

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

            operations.add(writer -> {
              if (toCollTypeName == null) {
                Optional<String> varLine = SingleVariableCopier.getLine(fromVarName, fromTypeQname, toVarName, toFields);
                if (varLine.isPresent()) {
                  writer.writeLine(varLine.get());
                } else {
                  ObjectPropsCopier.get(fromVarName, fromTypeQname, toVarName, toTypeQname, toFields, wireMethods)
                      .getLines().forEach(writer::writeLine);
                }
              } else {
                String elemQname = AstFind.qnameOfTypeRef(StringUtils.substringBetween(fromTypeQname, "<", ">"), ctx.cu);
                ObjectPropsCopier.get("each", elemQname, toVarName, toTypeQname, toFields, wireMethods)
                    .getLines().forEach(writer::writeLine);
              }
            });
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

            operations.add(writer -> {
              String varLine = SingleVariableCopier.getLine(fromVarName, cur, toVarName, toFields);
              writer.writeLine(varLine);
            });
            idxVarAmend = -1; varAmend = null;
          }
        }

        // Generate autowire method before fullfiling its body, to avoid cyclic autowiring
        String returnType = toCollTypeName==null ? toTypeQname : toCollTypeName+"<"+toTypeQname+">";
        PartWriter methodWriter = new PartWriter();
        Pair<AutowireMethod, Boolean> autowirer = genAutowireMethod(returnType, returnVar, paramTypes, params, methodWriter, wireMethods);

        // Modify the call place
        eventSeq.add(new EventSeq.Insertion(autowirer.getLeft().name, mi.getName().getStartPosition()));
        eventSeq.add(new EventSeq.Deletion(mi.getName()));
        for (Expression rmArg : removedArgs) {
          int begin = rmArg.getStartPosition();
          int nextComma = ctx.source.indexOf(',', rmArg.getStartPosition() + rmArg.getLength());
          if (nextComma <= 0) throw new RuntimeException("Wrong comma position after var amending arg!");
          int end = ctx.source.charAt(nextComma+1) == ' ' ? nextComma+2 : nextComma+1;
          eventSeq.add(new EventSeq.Deletion(begin, end));
        }
        if (!autowirer.getRight()) { // Is not new
          return;
        }

        operations.forEach(op -> op.accept(bodyWriter));

        if (toCollTypeName != null) {
          bodyWriter.writeLine(format("%s.add(%s);", returnVar, toVarName));
          bodyWriter.setIndent(2);
          bodyWriter.writeLine("}");
        }

        String firstParamName = StringUtils.substringAfter(params.get(0), " ");

        methodWriter.setIndent(1);
        methodWriter.writeLine(format("static %s %s(%s) {",
            returnType, autowirer.getLeft().name, String.join(", ", params)));
        methodWriter.setIndent(2);
        methodWriter.writeLine(format("if (%s == null) return null;\n", firstParamName));
        methodWriter.setIndent(0);
        bodyWriter.getLines().forEach(methodWriter::writeLine);
        methodWriter.setIndent(2);
        methodWriter.writeLine(format("return %s;", returnVar));
        methodWriter.setIndent(1);
        methodWriter.writeLine("}\n");
      }

    };
  }

  /** [method, isNew?] */
  static Pair<AutowireMethod, Boolean> genAutowireMethod(String returnType, String returnVar,
                                           List<String> paramTypes, List<String> params,
                                           PartWriter methodWriter, List<AutowireMethod> wireMethods) {
    if (paramTypes.size() != params.size()) throw new RuntimeException();

    String wireMethodName = "autowire"+ StringUtils.capitalize(StringUtils.removeStart(returnVar, "_"));
    AutowireMethod existing = existing(wireMethods, returnType, params);
    if (existing != null) return Pair.of(existing, false);

    String newMethodName = wireMethodName;
    int i = 2;
    while (existName(wireMethods, newMethodName)) {
      newMethodName = wireMethodName + i;
      i++;
    }
    wireMethodName = newMethodName;

    AutowireMethod autowireMethod = new AutowireMethod(wireMethodName, paramTypes, params, returnType, methodWriter);
    wireMethods.add(autowireMethod);
    return Pair.of(autowireMethod, true);
  }

  private static boolean existName(List<AutowireMethod> wireMethods, String name) {
    synchronized (wireMethods) {
      return wireMethods.stream().anyMatch(each -> each.name.equals(name));
    }
  }

  private static AutowireMethod existing(List<AutowireMethod> wireMethods, String retType, List<String> params) {
    synchronized (wireMethods) {
      return wireMethods.stream().filter(each -> each.retType.equals(retType) && each.params.equals(params))
          .findFirst().orElse(null);
    }
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

  private static String complexQname(Type type) {
    if (type.isParameterizedType()) {
      ParameterizedType ptype = (ParameterizedType) type;
      Iterator<String> qargs = ptype.typeArguments().stream().map(typeArg -> AstFind.qnameOfTypeRef(((Type) typeArg))).iterator();
      return ptype.getType().toString().trim()+"<"+StringUtils.join(qargs, ", ")+">";
    } else {
      return AstFind.qnameOfTypeRef(type);
    }
  }

  private static Map<String, String> javaCollections = new HashMap<>();
  static {
    javaCollections.put("Collection", "ArrayList");
    javaCollections.put("List", "ArrayList");
    javaCollections.put("Set", "HashSet");
  }
}
