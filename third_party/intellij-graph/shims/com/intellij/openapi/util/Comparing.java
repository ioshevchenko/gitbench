// Gitbench shim: a minimal replacement for the IntelliJ Platform class with the same name (Apache 2.0).
package com.intellij.openapi.util;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class Comparing {
  private Comparing() {
  }

  public static boolean equal(Object a, Object b) {
    return Objects.equals(a, b);
  }

  /** True when both collections hold the same elements with the same counts, in any order. */
  public static <T> boolean haveEqualElements(Collection<? extends T> a, Collection<? extends T> b) {
    if (a.size() != b.size()) {
      return false;
    }
    Map<T, Integer> counts = new HashMap<>();
    for (T t : a) {
      counts.merge(t, 1, Integer::sum);
    }
    for (T t : b) {
      Integer count = counts.get(t);
      if (count == null) {
        return false;
      }
      if (count == 1) {
        counts.remove(t);
      }
      else {
        counts.put(t, count - 1);
      }
    }
    return counts.isEmpty();
  }

  public static int unorderedHashcode(Collection<?> collection) {
    int hash = 0;
    for (Object o : collection) {
      hash += Objects.hashCode(o);
    }
    return hash;
  }
}
