package sorra.answerer;

import java.text.DecimalFormat;

import sorra.answerer.central.DoWire;
import sorra.answerer.central.ProjectGenerator;
import sorra.answerer.util.CommandProperties;

public class Main {
  public static void main(String[] args) throws Exception {
    long timeStart = System.currentTimeMillis();
    try {
      if (args.length == 0) {
        throw new IllegalArgumentException("Require a command");
      }
      String command = args[0];
      CommandProperties commandProperties = new CommandProperties("as-config.properties");
      String projectName = commandProperties.getProperty("project.name");
      String projectPath = commandProperties.getProperty("project.path", projectName);
      String basePackage = commandProperties.getProperty("base.package");
      String javaSubDir = commandProperties.getProperty("java.subdir", "src/main/java");

      ProjectGenerator.init(projectPath, projectName, basePackage, javaSubDir);
      if (command.equals("create")) {
        ProjectGenerator.create();
      } else if (command.equals("update")) {
        DoWire.run(projectPath, javaSubDir);
      }
    } finally {
      double timeCost = System.currentTimeMillis() - timeStart;
      System.out.printf("Answerer time cost: %s seconds\n", new DecimalFormat("0.000").format(timeCost / 1000));
    }
  }
}
