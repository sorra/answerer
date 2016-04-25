package sorra.answerer.central;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import sorra.answerer.io.FileUtil;
import sorra.answerer.util.StringUtil;
import sorra.answerer.util.TemplateEngine;

public class ProjectGenerator {
  private static final String TMPL_FOLDER = "src/main/resources/templates";

  private static String projectFolder;
  private static String projectName;
  private static String enterprise;

  public static void init(String workingDir, String projectName, String enterprisePackage) {
    projectFolder = workingDir.replace('\\', '/') + "/" + projectName;
    ProjectGenerator.projectName = projectName;
    enterprise = enterprisePackage;
  }

  public static void create() {
    try {
      FileUtils.copyDirectory(new File("project-template"), new File(projectFolder), false);

      exampleRenderAndWrite("build.gradle");
      exampleRenderAndWrite("settings.gradle");

      codeRenderAndWrite("Main.java");
      codeRenderAndWrite("util/Unbox.java");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void exampleRenderAndWrite(String subPath) {
    CharSequence buildGradle = TemplateEngine.render(new File("project-template", subPath), createMap());
    File file = new File(projectFolder, subPath);
    FileUtil.write(file, buildGradle);
    System.out.println("* Created file: " + file.getPath());
  }

  private static void codeRenderAndWrite(String subPath) {
    Path dest = Paths.get(projectFolder, "src/main/java/", enterprise.replace('.', '/'), subPath);
    CharSequence content = TemplateEngine.render(new File(TMPL_FOLDER, subPath), createMap());
    FileUtil.write(dest.toFile(), content);
    System.out.println("* Created file: " + dest);
  }

  public static void newController(String qnameEntity, String qnameXxx, String urlBase) {
    Map<String, String> map = createMap();
    map.put("urlBase", urlBase);
    String Entity = StringUtil.simpleName(qnameEntity);
    String Xxx = StringUtil.simpleName(qnameXxx);
    map.put("importXxx", "import "+qnameXxx+";\n");
    map.put("importEntity", "import "+qnameEntity+";\n");
    map.put("Xxx", Xxx);
    map.put("xxx", StringUtils.uncapitalize(Xxx));
    map.put("Entity", Entity);
    map.put("entity", StringUtils.uncapitalize(Entity));
    map.put("queryField", "");

    CharSequence controller = TemplateEngine.render(
        new File(TMPL_FOLDER+"/rest/Controller.java"), map);
    Path ctrlerPath = Paths.get(projectFolder, "src/main/java", enterprise.replace('.', '/'), "rest",
        Xxx+"Controller.java");
    FileUtil.write(ctrlerPath.toFile(), controller);
    System.out.println("* Created file: " + ctrlerPath);
  }

  public static void update() {

  }

  private static Map<String, String> createMap() {
    Map<String, String> map = new HashMap<>();
    map.put("projectName", projectName);
    map.put("enterprise", enterprise);
    return map;
  }
}
