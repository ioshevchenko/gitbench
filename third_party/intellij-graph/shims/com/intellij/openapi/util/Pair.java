// Gitbench shim: a minimal replacement for the IntelliJ Platform class with the same name (Apache 2.0).
package com.intellij.openapi.util;

import java.util.Objects;

public class Pair<A, B> {
  public final A first;
  public final B second;

  public Pair(A first, B second) {
    this.first = first;
    this.second = second;
  }

  public static <A, B> Pair<A, B> create(A first, B second) {
    return new Pair<>(first, second);
  }

  public static <A, B> Pair<A, B> pair(A first, B second) {
    return new Pair<>(first, second);
  }

  public final A getFirst() {
    return first;
  }

  public final B getSecond() {
    return second;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof Pair<?, ?> other && Objects.equals(first, other.first) && Objects.equals(second, other.second);
  }

  @Override
  public int hashCode() {
    return 31 * Objects.hashCode(first) + Objects.hashCode(second);
  }

  @Override
  public String toString() {
    return "<" + first + "," + second + ">";
  }
}
