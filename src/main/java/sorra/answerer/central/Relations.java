package sorra.answerer.central;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Relations {
  private static final Map<String, String> dto2Entity = new ConcurrentHashMap<>();

  public static void add(String dtoQname, String entityQname) {
    dto2Entity.put(dtoQname, entityQname);
  }

  public static String findEntity(String dtoQname) {
    return dto2Entity.get(dtoQname);
  }
}
