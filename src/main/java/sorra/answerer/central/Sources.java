package sorra.answerer.central;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.core.dom.CompilationUnit;
import sorra.answerer.ast.Parser;

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
    try {
      return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static CompilationUnit getCuByQname(String qname) {
    String source = getSourceByQname(qname);
    if (source == null) {
      throw new RuntimeException("No file for qname: " + qname);
    }
    return Parser.parse(source);
  }
}
