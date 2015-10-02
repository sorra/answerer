package sorra.answerer.api;

import java.util.concurrent.Callable;

public abstract class Interceptor {
  private Interceptor inner;

  public final Interceptor setInner(Interceptor inner) {
    if (inner == this) {
      throw new RuntimeException("Interceptor cannot refer itself as inner!");
    }
    this.inner = inner;
    return this;
  }

  public final <V> V invoke(Callable<V> f) throws Exception {
    try {
      before();
      V v;
      if (inner != null) {
        v = inner.invoke(f);
      } else {
        v = f.call();
      }
      done();
      return v;
    } catch (Throwable t) {
      fail(t);
      throw t;
    } finally {
      after();
    }
  }

  public void before() {
  }

  public void done() {
  }

  public void fail(Throwable t) {
  }

  public void after() {
  }
}
