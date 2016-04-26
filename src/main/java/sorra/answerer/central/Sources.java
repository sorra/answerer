package sorra.answerer.central;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.core.dom.CompilationUnit;
import sorra.answerer.ast.Parser;
import sorra.answerer.io.FileUtil;

public class Sources {
  private static Map<String, File> qnamesVsFiles = new ConcurrentHashMap<>();

  public static void add(String qname, File file) {
    qnamesVsFiles.put(qname, file);
  }

  public static String getSourceByQname(String qname) {
    File file = qnamesVsFiles.get(qname);
    if (file == null) {
      System.err.println("No file for qname: " + qname);
      return null;
    }
    return FileUtil.read(file);
  }

  public static CompilationUnit getCuByQname(String qname) {
    String source = getSourceByQname(qname);
    if (source == null) {
      throw new RuntimeException("No file for qname: " + qname);
    }
    return Parser.parse(source);
  }

  public static boolean containsQname(String qname) {
    return qnamesVsFiles.containsKey(qname);
  }
}
