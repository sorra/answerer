package sorra.answerer.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
public @interface Aop {
  Class<? extends Interceptor> value();
}
