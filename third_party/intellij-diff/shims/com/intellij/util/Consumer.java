package com.intellij.util;

/** Gitbench: copy of the IntelliJ interface. */
@FunctionalInterface
public interface Consumer<T> extends java.util.function.Consumer<T> {
  void consume(T t);

  @Override
  default void accept(T t) {
    consume(t);
  }
}
