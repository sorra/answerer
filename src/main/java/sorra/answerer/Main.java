package sorra.answerer;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Consumer;

import org.eclipse.jdt.core.dom.*;
import sorra.answerer.ast.AstFind;
import sorra.answerer.ast.FindUpper;
import sorra.answerer.ast.Parser;
import sorra.answerer.ast.VariableTypeResolver;
import sorra.answerer.central.ConfigReader;
import sorra.answerer.central.ObjectPropsCopier;
import sorra.answerer.central.SingleVariableCopier;
import sorra.answerer.central.Sources;
import sorra.answerer.io.FileUtil;
import sorra.answerer.io.FileWalker;

public class Main {
  private static String entityPackagePath;

  public static void main(String[] args) throws Exception {
    Properties paths = new Properties();
    try (InputStream fin = new FileInputStream("src/paths.properties")) {
      paths.load(fin);
    }
    String rootDir = paths.getProperty("root.dir");
    entityPackagePath = paths.getProperty("entity.package").replace('.', '/') + "/";

    Collection<File> files = FileWalker.findAll(new Path[]{Paths.get(rootDir)},
        path -> {
          String p = path.toString();
          return p.endsWith(".java") && !p.equals("package-info.java");
        });
    FileWalker.walkAll(files, fileAction(ConfigReader::read), 1);
    FileWalker.walkAll(files, fileAction(Main::process), 1);
  }

  private static Consumer<File> fileAction(Consumer<CompilationUnit> cuConsumer) {
    return file -> {
      try {
        CompilationUnit cu = Parser.parse(FileUtil.readFile(file.getPath()));
        cuConsumer.accept(cu);
      } catch (Throwable e) {
        System.err.println("Error at: " + file.getPath());
        e.printStackTrace();
      }
    };
  }

  static void process(CompilationUnit cu) {
    AbstractTypeDeclaration atd = (AbstractTypeDeclaration) cu.types().get(0);
    if (atd.getJavadoc() != null && atd.getJavadoc().toString().contains("User Function")) {
      atd.bodyDeclarations().stream().forEach(bodyDecl -> {
        if (bodyDecl instanceof MethodDeclaration) {
          ((MethodDeclaration)bodyDecl).getBody().accept(autowireVisitor());
        }
      });
    }
  }

  private static ASTVisitor autowireVisitor() {
    return new ASTVisitor() {
      @Override
      public boolean visit(MethodInvocation mi) {
        if (mi.getExpression().toString().trim().equals("Wirer")
            && mi.getName().getIdentifier().equals("autowire")) {
          Statement statement = FindUpper.statement(mi);
          System.out.println(statement);
          Type toTypeRef;
          SimpleName toVarName;
          if (statement instanceof ExpressionStatement) {
            VariableDeclarationExpression decl = (VariableDeclarationExpression)
                ((ExpressionStatement)statement).getExpression();
            toTypeRef = decl.getType();
            toVarName = ((VariableDeclarationFragment)decl.fragments().get(0)).getName();
          } else if (statement instanceof VariableDeclarationStatement) {
            VariableDeclarationStatement vds = (VariableDeclarationStatement) statement;
            toTypeRef = vds.getType();
            toVarName = ((VariableDeclarationFragment)vds.fragments().get(0)).getName();
          } else {
            throw new RuntimeException("Must declare a target variable. Statement: "+statement);
          }

          String toTypeQname = AstFind.qnameOfTypeRef(toTypeRef);
          TypeDeclaration toType = (TypeDeclaration) Sources.getCuByQname(toTypeQname).types().get(0);
          List<VariableDeclarationFragment> toFields = AstFind.fields(toType);

          List<Expression> args = mi.arguments();

          int idxVarAmend = -1;
          String varAmend = null;
          for (int i = 0; i < args.size(); i++) {
            Expression cur = args.get(i);
            if (cur instanceof StringLiteral) {
              String strLiteral = ((StringLiteral) cur).getLiteralValue();
              if (strLiteral.endsWith("=")) {
                idxVarAmend = i; varAmend = strLiteral;
                if (i == args.size()) throw new RuntimeException("Var amending cannot be the last arg");
              }
            } else {
              if (varAmend == null) {
                if (cur instanceof SimpleName == false) {
                  throw new RuntimeException("Var must be a SimpleName when without amending");
                }
                SimpleName fromVarName = (SimpleName) cur;
                String fromTypeQname = new VariableTypeResolver(fromVarName).resolveTypeQname();
                Optional<String> varLine = SingleVariableCopier.getLine(fromVarName.getIdentifier(), fromTypeQname,
                    toVarName.getIdentifier(), toFields);
                if (varLine.isPresent()) {
                  System.out.println(varLine.get());
                } else {
                  List<String> propLines = ObjectPropsCopier.get(fromVarName.getIdentifier(), fromTypeQname,
                      toVarName.getIdentifier(), toTypeQname, toFields).getLines();
                  propLines.forEach(System.out::println);
                }
              } else {
                if (idxVarAmend+1 != i) throw new RuntimeException("Var amending is not followed by var");
                String fromVarName = varAmend.substring(0, varAmend.length()-1);
                String varLine = SingleVariableCopier.getLine(fromVarName, cur, toVarName.getIdentifier(), toFields);
                System.out.println(varLine);
                idxVarAmend = -1; varAmend = null;
              }
            }
          }

        }
        return true;
      }
    };
  }
}