package com.intellij.openapi.util.registry;

/** Gitbench: returns the IntelliJ default values of the registry keys the diff code reads. */
public final class Registry {
  private Registry() {
  }

  public static boolean is(String key) {
    return switch (key) {
      case "diff.by.word.deprioritize.line.differences" -> false;
      default -> throw new IllegalArgumentException("Gitbench has no registry key " + key);
    };
  }
}
