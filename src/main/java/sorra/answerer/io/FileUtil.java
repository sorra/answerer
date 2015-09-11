package sorra.answerer.io;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class FileUtil {
  public static String readFile(String filePath) {
    try {
      return new String(Files.readAllBytes(new File(filePath).toPath()), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
