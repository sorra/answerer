package sorra.answerer;

import java.io.File;

import sorra.answerer.central.DoWire;
import sorra.answerer.central.ProjectGenerator;

public class Main {
  public static void main(String[] args) throws Exception {
    String theArgs = System.getProperty("args");

    String action, project, basePackage;
    if (theArgs != null) {
      String[] thoseArgs = theArgs.split(",");
      action = thoseArgs[0];
      project = thoseArgs[1];
      basePackage = thoseArgs[2];
    } else {
      action = System.getProperty("action");
      if (action == null) throw new IllegalArgumentException("action required");
      project = System.getProperty("project");
      if (project == null) throw new IllegalArgumentException("project required");
      basePackage = System.getProperty("basePackage");
      if (basePackage == null) throw new IllegalArgumentException("basePackage required");
    }
    ProjectGenerator.init(new File(".").getCanonicalPath(), project, basePackage);
    if (action.equals("create")) {
      ProjectGenerator.create();
    } else if (action.equals("update")) {
      DoWire.run(new File(project + "").getCanonicalPath(), "src/main/java", basePackage);
    }
  }
}
