// Gitbench shim: a minimal replacement for the IntelliJ Platform class with the same name (Apache 2.0).
package com.intellij.openapi.util.text;

import com.intellij.util.Function;

import java.util.Collection;

public final class StringUtil {
  private StringUtil() {
  }

  public static <T> String join(Collection<? extends T> items, Function<? super T, String> toString, String separator) {
    StringBuilder result = new StringBuilder();
    for (T item : items) {
      if (result.length() > 0) {
        result.append(separator);
      }
      result.append(toString.fun(item));
    }
    return result.toString();
  }
}
