package com.intellij.diff.util;

import com.intellij.openapi.editor.Document;

/** Gitbench: the methods of the IntelliJ DiffUtil that the ported code uses. */
public final class DiffUtil {
  private DiffUtil() {
  }

  public static int getLineCount(Document document) {
    return Math.max(document.getLineCount(), 1);
  }
}
