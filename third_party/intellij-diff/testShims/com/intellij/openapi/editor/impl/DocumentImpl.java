package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Document;

import java.util.ArrayList;
import java.util.List;

/** Gitbench: an immutable document over a string with '\n' line separators. */
public final class DocumentImpl implements Document {
  private final String text;
  private final int[] lineStarts;

  public DocumentImpl(CharSequence text) {
    this.text = text.toString();
    List<Integer> starts = new ArrayList<>();
    starts.add(0);
    for (int i = 0; i < this.text.length(); i++) {
      if (this.text.charAt(i) == '\n') {
        starts.add(i + 1);
      }
    }
    lineStarts = starts.stream().mapToInt(Integer::intValue).toArray();
  }

  @Override
  public CharSequence getCharsSequence() {
    return text;
  }

  /** Like IntelliJ: an empty document has no lines, and a trailing line separator starts an empty last line. */
  @Override
  public int getLineCount() {
    return text.isEmpty() ? 0 : lineStarts.length;
  }

  @Override
  public int getLineNumber(int offset) {
    int line = 0;
    while (line + 1 < lineStarts.length && lineStarts[line + 1] <= offset) {
      line++;
    }
    return line;
  }

  @Override
  public int getLineStartOffset(int line) {
    if (line == 0 && text.isEmpty()) {
      return 0;
    }
    return lineStarts[line];
  }

  @Override
  public int getLineEndOffset(int line) {
    if (line == 0 && text.isEmpty()) {
      return 0;
    }
    return line + 1 < lineStarts.length ? lineStarts[line + 1] - 1 : text.length();
  }

  @Override
  public int getLineSeparatorLength(int line) {
    return line + 1 < lineStarts.length ? 1 : 0;
  }
}
