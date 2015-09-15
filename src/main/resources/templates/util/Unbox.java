package $[enterprise].util;

public final class Unbox {
  private Unbox() {}

  public static boolean value(Boolean b) {
    return b == null ? false : b;
  }

  public static char value(Character c) {
    return c == null ? 0 : c;
  }

  public static byte value(Byte b) {
    return b == null ? 0 : b;
  }

  public static short value(Short s) {
    return s == null ? 0 : s;
  }

  public static int value(Integer i) {
    return i == null ? 0 : i;
  }

  public static long value(Long l) {
    return l == null ? 0 : l;
  }

  public static float value(Float f) {
    return f == null ? 0 : f;
  }

  public static double value(Double d) {
    return d == null ? 0 : d;
  }
}