// Gitbench shim: a minimal replacement for the IntelliJ Platform class with the same name (Apache 2.0).
package com.intellij.openapi.util;

@FunctionalInterface
public interface Condition<T> {
  boolean value(T t);
}
