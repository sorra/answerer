package com.example.aop;

import sorra.answerer.api.Aop;

@Aop(TxInterceptor.class)
public @interface Tx {
  boolean readOnly() default false;
}
