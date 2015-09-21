package sorra.answerer.central;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class PartWriter {
  private List<String> lines = new LinkedList<>();
  private List<String> importNames = new LinkedList<>();
  private int indent;

  public void setIndent(int indent) {
    this.indent = indent;
  }

  public void writeLine(String line) {
    if (indent > 0) {
      char[] spaces = new char[indent*2];
      Arrays.fill(spaces, ' ');
      line = String.valueOf(spaces) + line;
    }
    lines.add(line);
  }

  public void addImport(String importName) {
    importNames.add(importName);
  }

  public List<String> getLines() {
    return lines;
  }

  public List<String> getImportNames() {
    return importNames;
  }
}
