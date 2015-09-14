package sorra.answerer;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jdt.core.dom.*;
import sorra.answerer.ast.*;
import sorra.answerer.central.*;
import sorra.answerer.io.FileUtil;
import sorra.answerer.io.FileWalker;
import sorra.answerer.util.EventSeq;
import sorra.answerer.util.PrimitiveUtil;
import sorra.answerer.util.StringUtil;

public class Main {
  private static String rootDir;
  private static String genDir;
  private static String enterprisePackagePath;

  public static void main(String[] args) throws Exception {
    Properties paths = new Properties();
    try (InputStream fin = new FileInputStream("src/paths.properties")) {
      paths.load(fin);
    }
    rootDir = paths.getProperty("root.dir");
    genDir = paths.getProperty("gen.dir");
    enterprisePackagePath = paths.getProperty("enterprise.package").replace('.', '/');

    Collection<File> files = FileWalker.findAll(new Path[]{Paths.get(rootDir)},
        path -> {
          String p = path.toString();
          return p.endsWith(".java") && !p.equals("package-info.java");
        });
    FileWalker.walkAll(files, fileAction(ctx -> ConfigReader.read(ctx.cu)), 1);
    FileWalker.walkAll(files, fileAction(Main::process), 1);
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

  static void process(AstContext ctx) {
    AbstractTypeDeclaration atd = (AbstractTypeDeclaration) ctx.cu.types().get(0);
    if (atd.getJavadoc() != null && atd.getJavadoc().toString().contains("User Function")) {
      EventSeq eventSeq = new EventSeq(ctx.source);

      atd.bodyDeclarations().stream().forEach(bodyDecl -> {
        if (bodyDecl instanceof MethodDeclaration) {
          ((MethodDeclaration) bodyDecl).getBody().accept(autowireVisitor(ctx, eventSeq));
        }
      });

      System.out.println(eventSeq.run());

      PackageDeclaration pkgDecl = ctx.cu.getPackage();
      String pkg = pkgDecl.getName().getFullyQualifiedName();
      FileWriter fileWriter = new FileWriter(new File(rootDir + "/" + pkg.replace('.', '/') + "/Wirer.java"));
    }
  }

  private static ASTVisitor autowireVisitor(AstContext ctx, EventSeq eventSeq) {
    return new ASTVisitor() {
      @Override
      public boolean visit(MethodInvocation mi) {
        if (mi.getExpression().toString().trim().equals("Wirer")
            && mi.getName().getIdentifier().equals("autowire")) {
          ctx.modified = true;
          Statement statement = FindUpper.statement(mi);
          System.out.println(statement);
          Type toTypeRef;
          String toVarName;
          if (statement instanceof ReturnStatement && mi == ((ReturnStatement) statement).getExpression()) {
            MethodDeclaration md = FindUpper.methodScope(statement);
            assert md != null;
            toTypeRef = md.getReturnType2();
            Type elemType;
            if (toTypeRef instanceof ParameterizedType) {
              elemType = (Type) ((ParameterizedType) toTypeRef).typeArguments().get(0);
            } else if (toTypeRef instanceof ArrayType) {
              elemType = ((ArrayType) toTypeRef).getElementType();
            } else {
              elemType = toTypeRef;
            }
            toVarName = StringUtils.uncapitalize(AstFind.snameOfTypeRef(elemType));
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

          String toTypeQname = AstFind.qnameOfTypeRef(toTypeRef);
          TypeDeclaration toTypeDecl = (TypeDeclaration) Sources.getCuByQname(toTypeQname).types().get(0);
          List<VariableDeclarationFragment> toFields = AstFind.fields(toTypeDecl);

          List<Expression> args = mi.arguments();

          List<Expression> removedArgs = new ArrayList<>();
          List<String> params = new ArrayList<>();
          List<String> lines = new ArrayList<>();
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
              params.add(fromTypeQname + " " + fromVarName);

              Optional<String> varLine = SingleVariableCopier.getLine(fromVarName, fromTypeQname, toVarName, toFields);
              if (varLine.isPresent()) {
                lines.add(varLine.get());
              } else {
                ObjectPropsCopier.get(fromVarName, fromTypeQname, toVarName, toTypeQname, toFields)
                    .getLines().forEach(lines::add);
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
              params.add(PrimitiveUtil.boxType(fieldTypeQname) + " " + fromVarName);

              String varLine = SingleVariableCopier.getLine(fromVarName, cur, toVarName, toFields);
              lines.add(varLine);
              idxVarAmend = -1; varAmend = null;
            }
          }

          String wireMethodName = "autowire"+ StringUtil.simpleName(toTypeQname);

          SimpleName methName = mi.getName();
          eventSeq.add(new EventSeq.Insertion(wireMethodName, methName.getStartPosition()));
          eventSeq.add(new EventSeq.Deletion(
              methName.getStartPosition(), methName.getStartPosition() + methName.getLength()));
          for (Expression rmArg : removedArgs) {
            int begin = rmArg.getStartPosition();
            int nextComma = ctx.source.indexOf(',', rmArg.getStartPosition() + rmArg.getLength());
            if (nextComma <= 0) throw new RuntimeException("Wrong comma position after var amending arg!");
            int end = ctx.source.charAt(nextComma+1) == ' ' ? nextComma+2 : nextComma+1;
            eventSeq.add(new EventSeq.Deletion(begin, end));
          }

          PartWriter wireMethodWriter = new PartWriter();
          wireMethodWriter.setIndent(1);
          wireMethodWriter.writeLine(String.format("public static %s %s(%s) {",
              toTypeQname, wireMethodName, String.join(", ", params)));
          wireMethodWriter.setIndent(2);
          wireMethodWriter.writeLine(String.format("%s %s = new %s();", toTypeQname, toVarName, toTypeQname));
          lines.forEach(wireMethodWriter::writeLine);
          wireMethodWriter.writeLine(String.format("return %s;", toVarName));
          wireMethodWriter.setIndent(1);
          wireMethodWriter.writeLine("}");
          wireMethodWriter.getLines().forEach(System.out::println);
        }
        return true;
      }
    };
  }
}
