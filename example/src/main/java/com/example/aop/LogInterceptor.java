package com.example.aop;

import sorra.answerer.api.Interceptor;

public class LogInterceptor extends Interceptor {
  @Override
  public void before() {
    System.out.println("Log before.");
  }

  @Override
  public void done() {
    System.out.println("Log done.");
  }

  @Override
  public void fail(Throwable t) {
    System.err.println("Log fail.");
  }
}
