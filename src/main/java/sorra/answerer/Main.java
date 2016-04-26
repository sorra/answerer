package sorra.answerer;

import java.io.File;

import sorra.answerer.central.DoWire;
import sorra.answerer.central.ProjectGenerator;
import sorra.answerer.util.CommandProperties;

public class Main {
  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      throw new IllegalArgumentException("Require a command");
    }
    String command = args[0];
    CommandProperties commandProperties = new CommandProperties("config.properties");
    String projectName = commandProperties.getProperty("project.name");
    String basePackage = commandProperties.getProperty("base.package");
    String javaSubDir = commandProperties.getProperty("java.subdir", "src/main/java");

    ProjectGenerator.init(new File(".").getCanonicalPath(), projectName, basePackage);
    if (command.equals("create")) {
      ProjectGenerator.create();
    } else if (command.equals("update")) {
      DoWire.run(new File(projectName).getCanonicalPath(), javaSubDir);
    }
  }
}
