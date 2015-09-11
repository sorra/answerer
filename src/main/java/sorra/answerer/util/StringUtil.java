package sorra.answerer.util;

public class StringUtil {
  public static boolean isCapital(String s) {
    if (s == null || s.isEmpty()) return false;
    return Character.isUpperCase(s.charAt(0));
  }

  public static boolean isNotCapital(String s) {
    return !isCapital(s);
  }
}
