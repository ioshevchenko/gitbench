// Gitbench shim: a minimal replacement for the IntelliJ Platform class with the same name (Apache 2.0).
package com.google.common.primitives;

import java.util.Collection;

public final class Ints {
  private Ints() {
  }

  public static int[] toArray(Collection<? extends Number> collection) {
    int[] result = new int[collection.size()];
    int i = 0;
    for (Number n : collection) {
      result[i++] = n.intValue();
    }
    return result;
  }
}
