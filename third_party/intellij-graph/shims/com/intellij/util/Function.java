// Gitbench shim: a minimal replacement for the IntelliJ Platform class with the same name (Apache 2.0).
package com.intellij.util;

@FunctionalInterface
public interface Function<Param, Result> {
  Result fun(Param param);
}
