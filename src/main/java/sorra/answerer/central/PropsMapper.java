package sorra.answerer.central;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

public class PropsMapper {
  private static Map<Pair<String, String>, Map<String, String>> propsMappings = new ConcurrentHashMap<>();

  public static void bidirectMapProp(String qname1, String prop1, String qname2, String prop2) {
    directMapProp(qname1, prop1, qname2, prop2);
    directMapProp(qname2, prop2, qname1, prop1);
  }

  private static void directMapProp(String qname1, String prop1, String qname2, String prop2) {
    prop1 = toPropNameIfGetter(prop1);
    prop2 = toPropNameIfGetter(prop2);
    Map<String, String> propsMapping;
    synchronized (propsMappings) { // Pessimistic lock
      propsMapping = propsMappings.get(Pair.of(qname1, qname2));
      if (propsMapping == null) {
        propsMapping = new ConcurrentHashMap<>();
        propsMappings.put(Pair.of(qname1, qname2), propsMapping);
      }
    }
    propsMapping.put(prop1, prop2);
  }
  public static boolean isTypePairMapped(String qname1, String qname2) {
    return propsMappings.get(Pair.of(qname1, qname2)) != null;
  }

  public static String findMappedProp(String toQname, String toProp, String fromQname) {
    Map<String, String> propsMapping = propsMappings.get(Pair.of(toQname, fromQname));
    if (propsMapping != null) {
      return propsMapping.get(toProp);
    }
    return null;
  }

  private static String toPropNameIfGetter(String getter) {
    if (getter.endsWith("()") && getter.startsWith("get") && getter.length() > 5) {
      char firstCh = getter.charAt(3); // right after "get"
      return Character.toLowerCase(firstCh) + getter.substring(4, getter.length() - 2);
    } else {
      return getter;
    }
  }
}
