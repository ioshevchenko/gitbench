// Gitbench shim: a minimal replacement for the IntelliJ Platform class with the same name (Apache 2.0).
package com.intellij.util;

import java.util.ArrayList;
import java.util.Collection;

/** IntelliJ keeps one element without an array; a plain ArrayList has the same behavior. */
public class SmartList<E> extends ArrayList<E> {
  public SmartList() {
    super(1);
  }

  public SmartList(E element) {
    super(1);
    add(element);
  }

  public SmartList(Collection<? extends E> elements) {
    super(elements);
  }
}
