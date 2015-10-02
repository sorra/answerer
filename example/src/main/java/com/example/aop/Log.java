package com.example.aop;

import sorra.answerer.api.Aop;

@Aop(LogInterceptor.class)
public @interface Log {
}
