package sorra.answerer.constant;

import java.util.HashMap;
import java.util.Map;

import sorra.answerer.util.StringUtil;

public class JavaCollections {
  private static Map<String, String> javaCollections = new HashMap<>();
  static {
    javaCollections.put("Collection", "ArrayList");
    javaCollections.put("List", "ArrayList");
    javaCollections.put("Set", "HashSet");
    javaCollections.put("java.util.Collection", "ArrayList");
    javaCollections.put("java.util.List", "ArrayList");
    javaCollections.put("java.util.Set", "HashSet");
  }

  public static String get(String intf) {
    return javaCollections.get(intf);
  }

  public static boolean containsIntf(String intf) {
    return javaCollections.containsKey(intf);
  }

  public static String collName(String qname) {
    return StringUtil.simpleName(qname);
  }
}
