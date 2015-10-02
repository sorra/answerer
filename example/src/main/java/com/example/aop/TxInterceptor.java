package com.example.aop;

import sorra.answerer.api.Interceptor;

public class TxInterceptor extends Interceptor {
  private boolean readOnly;

  public TxInterceptor(boolean readOnly) {
    this.readOnly = readOnly;
  }

  @Override
  public void before() {
    System.out.println("Tx begin.");
  }

  @Override
  public void done() {
    System.out.println("Tx commit.");
  }

  @Override
  public void fail(Throwable t) {
    System.err.println("Tx rollback.");
  }
}
