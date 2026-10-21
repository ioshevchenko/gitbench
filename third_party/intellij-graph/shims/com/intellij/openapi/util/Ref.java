// Gitbench shim: a minimal replacement for the IntelliJ Platform class with the same name (Apache 2.0).
package com.intellij.openapi.util;

public final class Ref<T> {
  private T value;

  public Ref() {
  }

  public Ref(T value) {
    this.value = value;
  }

  public static <T> Ref<T> create(T value) {
    return new Ref<>(value);
  }

  public static <T> Ref<T> create() {
    return new Ref<>();
  }

  public T get() {
    return value;
  }

  public void set(T value) {
    this.value = value;
  }

  public boolean isNull() {
    return value == null;
  }
}
