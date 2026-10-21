// Gitbench shim: a minimal replacement for the IntelliJ Platform class with the same name (Apache 2.0).
package com.intellij.util.containers;

import java.util.AbstractList;

/** A list that rejects changes. Subclasses give get() and size(). */
public abstract class ImmutableList<E> extends AbstractList<E> {
}
