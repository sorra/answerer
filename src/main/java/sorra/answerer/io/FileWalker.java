package sorra.answerer.io;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import sorra.answerer.central.Sources;

public class FileWalker {
  public static Collection<File> findAll(Path[] roots, Predicate<Path> fileFilter) {
    return Stream.of(roots).flatMap(root -> {
      try {
        return Files.walk(root).filter(fileFilter);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }).map(path1 -> {
      File file = path1.toFile();

      final String path = file.getPath().replace('\\', '/');
      final String _java_ = "/java/";
      if (path.contains(_java_)) {
        String qname = path.substring(path.indexOf(_java_) + _java_.length())
            .replace(".java", "").replace('/', '.');
        if (!qname.contains(".")) throw new RuntimeException("Bad qname for file: " + path);
        Sources.add(qname, file);
      }
      return file;
    }).collect(Collectors.toList());
  }

  public static void walkAll(Collection<File> files, Consumer<File> fileAction, int maxMinutes) {
    try {
      ExecutorService es = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

      files.forEach(file -> es.submit(() -> fileAction.accept(file)));

      es.shutdown();
      es.awaitTermination(maxMinutes, TimeUnit.MINUTES);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
