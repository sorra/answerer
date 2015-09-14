package sorra.answerer.central;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import sorra.answerer.util.StringUtil;
import sorra.answerer.util.TemplateEngine;

public class ProjectGenerator {
  private static String folder;
  private static String enterprise;

  public static void init(String projectFolder, String enterprisePackage) {
    folder = projectFolder;
    enterprise = enterprisePackage;
  }

  public static void newEntity(String entityQname, String... dtoQnames) {
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
        new File("src/main/resources/templates/rest/Controller.java"), map);
    try {
      Path rest = Paths.get(folder, "src/main/java", enterprise.replace('.', '/'), "rest");
      Files.createDirectories(rest);
      Files.write(Paths.get(rest.toString(), entityName + "Controller.java"),
          controller.toString().getBytes(StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static void update() {

  }

  private static Map<String, String> createMap() {
    Map<String, String> map = new HashMap<>();
    map.put("enterprise", enterprise);
    return map;
  }

  public static void main(String[] args) throws IOException {
    init(new File(".").getCanonicalPath() + "/space", "com.space");
    newEntity("com.space.User", "com.space.UserView");
  }
}
