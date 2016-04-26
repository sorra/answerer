package sorra.answerer.central;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.core.dom.*;
import sorra.answerer.ast.AstContext;
import sorra.answerer.ast.AstFind;
import sorra.answerer.ast.FindUpper;
import sorra.answerer.ast.VariableTypeResolver;
import sorra.answerer.constant.JavaCollections;
import sorra.answerer.io.FileUtil;
import sorra.answerer.util.EventSeq;
import sorra.answerer.util.PrimitiveUtil;
import sorra.answerer.util.StringUtil;

import static java.lang.String.format;

class Autowire {
  private static Map<String, List<AutowireMethod>> pkgs2Wirers = new ConcurrentHashMap<>();

  //Better to wrap wirer methods into synchronized lambda-structure, but Java 8 has bad type-inference
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
      File file = new File(javaFolder + "/" + pkg.replace('.', '/') + "/Wirer.java");
      FileUtil.write(file, wirer.getWhole());
      System.out.println("* Created file: " + file.getPath());
    });
  }

  static ASTVisitor visitor(AstContext ctx, EventSeq eventSeq, List<AutowireMethod> wireMethods) {
    return new ASTVisitor() {

      @Override
      public boolean visit(MethodInvocation mi) {
        if (mi.getName().getIdentifier().equals("autowire")
            && mi.getExpression() != null && mi.getExpression().toString().trim().equals("Wirer")) {
          rewriteCallsite(mi);
        }
        return true;
      }

      void rewriteCallsite(MethodInvocation mi) {
        ctx.modified = true;
        Type toTypeRef = getToTypeRef(mi);

        String toCollTypeName = ((Supplier<String>) () -> {
          if (toTypeRef.isParameterizedType()) {
            ParameterizedType genericType = (ParameterizedType) toTypeRef;
            String rawTypeName = genericType.getType().toString().trim();
            if (JavaCollections.containsIntf(rawTypeName)) {
              return StringUtil.simpleName(rawTypeName);
            }
          }
          return null;
        }).get();

        String toEtalQname = AstFind.qnameOfTypeRef(getElementalType(toTypeRef));

        List<Expression> removedArgs = new ArrayList<>();
        List<String> paramTypes = new ArrayList<>();
        List<String> params = new ArrayList<>();
        List<Consumer<PartWriter>> copyings = handleArguments(
            toCollTypeName != null, toEtalQname, removedArgs, paramTypes, params, mi.arguments());

        Consumer<AutowireMethod> callsiteAction = am -> {
          // Modify the callsite
          eventSeq.add(new EventSeq.Insertion(am.name, mi.getName().getStartPosition()));
          eventSeq.add(new EventSeq.Deletion(mi.getName()));
          for (Expression rmArg : removedArgs) {
            int begin = rmArg.getStartPosition();
            int nextComma = ctx.source.indexOf(',', rmArg.getStartPosition() + rmArg.getLength());
            if (nextComma <= 0) throw new RuntimeException("Wrong comma position after var amending arg!");
            int end = ctx.source.charAt(nextComma+1) == ' ' ? nextComma+2 : nextComma+1;
            eventSeq.add(new EventSeq.Deletion(begin, end));
          }
        };

        String fromCollVar = null;
        String fromEtalQname = null;
        if (toCollTypeName != null) {
          // In collection mode the first arg is source collection
          SimpleName srcColl = (SimpleName) mi.arguments().get(0);
          fromCollVar = srcColl.getIdentifier();
          Type srcCollType = new VariableTypeResolver(srcColl).resolveType();
          if (srcCollType instanceof ParameterizedType) {
            fromEtalQname = AstFind.qnameOfTypeRef((Type) ((ParameterizedType) srcCollType).typeArguments().get(0));
          } else {
            throw new RuntimeException("First arg must have generic type like List<E>!");
          }
        }
        WiringParams wp = new WiringParams(fromEtalQname, toEtalQname, toCollTypeName, fromCollVar, params, paramTypes);
        genAutowireMethod(wp, callsiteAction, wireMethods, copyings);
      }

      private List<Consumer<PartWriter>> handleArguments(
          boolean collectionMode, String toTypeQname, List<Expression> removedArgs,
          List<String> paramTypes, List<String> params, List<Expression> args) {
        String toEtalVar = "$r";
        List<VariableDeclarationFragment> toFields = AstFind.fields(toTypeQname);
        List<Consumer<PartWriter>> writes = new ArrayList<>(); // Defered writes

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

            writes.add(writer -> {
              if (collectionMode) {
                String elemQname = AstFind.qnameOfTypeRef(StringUtils.substringBetween(fromTypeQname, "<", ">"), ctx.cu);
                ObjectPropsCopier.get("each", elemQname, toEtalVar, toTypeQname, toFields, wireMethods)
                    .getLines().forEach(writer::writeLine);
              } else {
                Optional<String> varLine = SingleVariableCopier.getLine(fromVarName, toEtalVar, toFields);
                if (varLine.isPresent()) {
                  writer.writeLine(varLine.get());
                } else {
                  ObjectPropsCopier.get(fromVarName, fromTypeQname, toEtalVar, toTypeQname, toFields, wireMethods)
                      .getLines().forEach(writer::writeLine);
                }
              }
            });
          } else {
            if (idxVarAmend+1 != i) throw new RuntimeException("Var amending is not followed by var expression!");
            String fromVarName = varAmend.substring(0, varAmend.length()-1);

            Optional<VariableDeclarationFragment> matchField = toFields.stream()
                .filter(x -> x.getName().getIdentifier().equals(fromVarName)).findFirst();
            if (!matchField.isPresent()) {
              throw new RuntimeException(format("Bad var amending '%s' for object '%s'", fromVarName, toEtalVar));
            }
            Type type = ((FieldDeclaration) matchField.get().getParent()).getType();
            String fieldTypeQname = AstFind.qnameOfTypeRef(type);

            //Box primitive params, to escape from NPE
            String boxType = PrimitiveUtil.boxType(fieldTypeQname);
            paramTypes.add(boxType);
            params.add(boxType + " " + fromVarName);

            writes.add(writer -> {
              writer.writeLine(SingleVariableCopier.getLine(fromVarName, cur, toEtalVar, toFields));
            });
            idxVarAmend = -1; varAmend = null;
          }
        }
        return writes;
      }

    };
  }

  private static Type getToTypeRef(MethodInvocation mi) {
    Statement statement = FindUpper.statement(mi);
    Type toTypeRef;
    if (statement instanceof ReturnStatement && mi == ((ReturnStatement) statement).getExpression()) {
      MethodDeclaration md = FindUpper.methodScope(statement);
      assert md != null;
      toTypeRef = md.getReturnType2();
    } else if (statement instanceof ExpressionStatement) {
      VariableDeclarationExpression decl = (VariableDeclarationExpression)
          ((ExpressionStatement)statement).getExpression();
      toTypeRef = decl.getType();
    } else if (statement instanceof VariableDeclarationStatement) {
      VariableDeclarationStatement vds = (VariableDeclarationStatement) statement;
      toTypeRef = vds.getType();
    } else {
      throw new RuntimeException("Must declare a target variable. Statement: "+statement);
    }
    return toTypeRef;
  }

  static AutowireMethod genAutowireMethod(WiringParams wp, Consumer<AutowireMethod> callsiteAction,
                                          List<AutowireMethod> wireMethods, List<Consumer<PartWriter>> copyings) {
    // Generate autowire method before fullfiling its body, to avoid cyclic autowiring
    PartWriter methodWriter = new PartWriter();
    Pair<AutowireMethod, Boolean> autowirer = genAutowireMethodHeader(wp.returnTypeName, wp.defaultMethodName,
        wp.parameterTypes, wp.parameters, methodWriter, wireMethods);

    callsiteAction.accept(autowirer.getLeft());
    if (!autowirer.getRight()) {
      return autowirer.getLeft();
    }

    PartWriter bodyWriter = new PartWriter();
    bodyWriter.setIndent(2);
    if (wp.collectionMode()) {
      bodyWriter.writeLine(format("%s<%s> %s = new java.util.%s<>();",
          wp.toCollTypeName, wp.toEtalQname, wp.toCollVar, JavaCollections.get(wp.toCollTypeName)));
      bodyWriter.writeLine(format("for (%s each : %s) {", wp.fromEtalQname, wp.fromCollVar));
      bodyWriter.indent();
    }
    bodyWriter.writeLine(format("%s %s = new %s();", wp.toEtalQname, wp.toEtalVar, wp.toEtalQname));
    copyings.forEach(each -> each.accept(bodyWriter));
    if (wp.collectionMode()) {
      bodyWriter.writeLine(format("%s.add(%s);", wp.toCollVar, wp.toEtalVar));
      bodyWriter.setIndent(2);
      bodyWriter.writeLine("}");
    }

    String firstParamName = StringUtils.substringAfter(wp.parameters.get(0), " ");
    methodWriter.setIndent(1);
    methodWriter.writeLine(format("static %s %s(%s) {",
        wp.returnTypeName, autowirer.getLeft().name, String.join(", ", wp.parameters)));
    methodWriter.setIndent(2);
    methodWriter.writeLine(format("if (%s == null) return null;\n", firstParamName));
    methodWriter.setIndent(0);
    bodyWriter.getLines().forEach(methodWriter::writeLine);
    methodWriter.setIndent(2);
    methodWriter.writeLine(format("return %s;", wp.collectionMode() ? wp.toCollVar : wp.toEtalVar));
    methodWriter.setIndent(1);
    methodWriter.writeLine("}\n");
    return autowirer.getLeft();
  }

  /** [method, isNew?] */
  static Pair<AutowireMethod, Boolean> genAutowireMethodHeader(String returnType, String defaultMethodName,
                                                               List<String> paramTypes, List<String> params,
                                                               PartWriter methodWriter, List<AutowireMethod> wireMethods) {
    if (paramTypes.size() != params.size()) throw new RuntimeException();

    String wireMethodName = defaultMethodName;
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

  static class WiringParams {
    final List<String> parameters;
    final List<String> parameterTypes;
    final String toCollTypeName;

    // Etal == Elemental (simple type or generic T)
    final String fromEtalQname;
    final String toEtalQname;

    final String fromCollVar;
    final String toCollVar = "$rs";
    final String toEtalVar = "$r";

    final String returnTypeName;
    final String defaultMethodName;

    WiringParams(String fromEtalQname, String toEtalQname, String toCollTypeName,
                 String fromCollVar, List<String> parameters, List<String> parameterTypes) {
      this.fromEtalQname = fromEtalQname;
      this.toEtalQname = toEtalQname;
      this.fromCollVar = fromCollVar;
      this.toCollTypeName = toCollTypeName;
      this.parameters = parameters;
      this.parameterTypes = parameterTypes;
      returnTypeName = collectionMode() ? this.toCollTypeName +"<"+toEtalQname+">" : toEtalQname;
      String toSname = StringUtil.simpleName(toEtalQname);
      defaultMethodName = "autowire" + (collectionMode() ? toSname+"s" : toSname);
    }

    boolean collectionMode() {
      return toCollTypeName != null;
    }
  }
}
