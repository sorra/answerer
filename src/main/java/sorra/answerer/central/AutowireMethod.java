package sorra.answerer.central;

import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class AutowireMethod {
  String name;
  List<String> paramTypes;
  List<String> params;
  String retType;
  PartWriter code;

  public AutowireMethod(String name, List<String> paramTypes, List<String> params, String retType, PartWriter code) {
    this.name = name;
    this.paramTypes = paramTypes;
    for (String param : params) {
      if (param.indexOf(' ') <= 0) {
        throw new IllegalArgumentException();
      }
    }
    this.params = params;
    this.retType = retType;
    this.code = code;
  }
}
