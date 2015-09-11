package sorra.answerer.ast;

import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

public class Parser {
  static Map<String, String> compilerOptions = JavaCore.getOptions();
  static {
    compilerOptions.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_1_8);
    compilerOptions.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, JavaCore.VERSION_1_8);
    compilerOptions.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_8);
  }

  public static CompilationUnit parse(String source) {
    ASTParser parser = ASTParser.newParser(AST.JLS8);
    parser.setKind(ASTParser.K_COMPILATION_UNIT);
    parser.setCompilerOptions(compilerOptions);
    parser.setSource(source.toCharArray());
    return (CompilationUnit) parser.createAST(null);
  }
}
