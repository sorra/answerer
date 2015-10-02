package com.example.aop;

import java.io.IOException;

import sorra.answerer.api.ImpossibleException;

/**
 * $UserFunction
 */
class UserSupport {

  @Log @Tx(readOnly = true)
  boolean act(Object o) throws IOException {
    Object p = o;
    synchronized (p) {
      System.out.println(o);
      return true;
    }
  }

}
