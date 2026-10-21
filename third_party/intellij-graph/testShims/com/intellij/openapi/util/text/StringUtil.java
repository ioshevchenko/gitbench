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

  public static boolean equals(CharSequence s1, CharSequence s2) {
    if (s1 == null || s2 == null) {
      return s1 == s2;
    }
    return s1.toString().contentEquals(s2);
  }

  public static boolean equalsIgnoreWhitespaces(CharSequence s1, CharSequence s2) {
    return withoutWhitespaces(s1).equals(withoutWhitespaces(s2));
  }

  private static String withoutWhitespaces(CharSequence s) {
    StringBuilder result = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      if (!Character.isWhitespace(s.charAt(i))) {
        result.append(s.charAt(i));
      }
    }
    return result.toString();
  }

  public static boolean endsWithChar(CharSequence s, char suffix) {
    return s != null && s.length() != 0 && s.charAt(s.length() - 1) == suffix;
  }

  public static boolean containsChar(String value, char ch) {
    return value.indexOf(ch) >= 0;
  }
}
