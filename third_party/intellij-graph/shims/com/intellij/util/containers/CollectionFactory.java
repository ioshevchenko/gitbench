// Gitbench shim: a minimal replacement for the IntelliJ Platform class with the same name (Apache 2.0).
package com.intellij.util.containers;

import java.util.HashSet;
import java.util.Set;

public final class CollectionFactory {
  private CollectionFactory() {
  }

  public static <T> Set<T> createSmallMemoryFootprintSet() {
    return new HashSet<>();
  }
}
