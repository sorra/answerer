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

  private static void exampleRenderAndWrite(String subPath) throws IOException {
    CharSequence buildGradle = TemplateEngine.render(new File("project-template", subPath), createMap());
    FileUtils.write(new File(projectFolder, subPath), buildGradle, StandardCharsets.UTF_8);
  }

  private static void codeRenderAndWrite(String subPath) throws IOException {
    Path dest = Paths.get(projectFolder, "src/main/java/", enterprise.replace('.', '/'), subPath);
    CharSequence content = TemplateEngine.render(new File(TMPL_FOLDER, subPath), createMap());
    FileUtils.write(dest.toFile(), content, StandardCharsets.UTF_8);
  }

  public static void newController(String entityQname, String... dtoQnames) {
    Map<String, String> map = createMap();
    String entityPackage = StringUtil.qualifier(entityQname);
    String entityName = StringUtil.simpleName(entityQname);
    map.put("XxxPackage", entityPackage);
    map.put("Xxx", entityName);
    for (String dtoQn : dtoQnames) {
      String dtoPackage = StringUtil.qualifier(dtoQn);
      String dtoName = StringUtil.simpleName(dtoQn);
      if (dtoName.equals(entityName+"View")) {
        map.put("XxxViewPackage", dtoPackage);
        map.put("XxxView", dtoName);
      } else if (dtoName.equals(entityName+"Preview")) {
        map.put("XxxPreviewPackage", dtoPackage);
        map.put("XxxPreview", dtoName);
      }
    }

    if (map.get("XxxView") == null) {
      map.put("XxxViewPackage", entityPackage);
      map.put("XxxView", entityName);
    }
    if (map.get("XxxPreview") == null) {
      map.put("XxxPreviewPackage", entityPackage);
      map.put("XxxPreview", entityName);
    }
    map.put("xxx", StringUtils.uncapitalize(map.get("Xxx")));
    map.put("queryField", "");

    CharSequence controller = TemplateEngine.render(
        new File(TMPL_FOLDER+"/rest/Controller.java"), map);
    try {
      Path ctrlerPath = Paths.get(projectFolder, "src/main/java", enterprise.replace('.', '/'), "rest",
          entityName+"Controller.java");
      FileUtils.write(ctrlerPath.toFile(), controller, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static void update() {

  }

  private static Map<String, String> createMap() {
    Map<String, String> map = new HashMap<>();
    map.put("projectName", projectName);
    map.put("enterprise", enterprise);
    return map;
  }

  public static void main(String[] args) throws IOException {
    init(new File(".").getCanonicalPath(), "example", "com.example");
    create();
    newController("com.example.entity.User", "com.example.dto.UserView");
  }
}
