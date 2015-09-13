package sorra.answerer.central;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class FileWriter {
  private File file;
  private List<String> lines = new LinkedList<>();
  private int indent;

  public FileWriter(File file) {
    this.file = file;
  }

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

  public void complete() {
    try {
      Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
