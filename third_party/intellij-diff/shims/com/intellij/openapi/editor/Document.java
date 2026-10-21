package com.intellij.openapi.editor;

/** Gitbench: the read-only part of the IntelliJ document that the diff code and its tests use. */
public interface Document {
  CharSequence getCharsSequence();

  default CharSequence getImmutableCharSequence() {
    return getCharsSequence();
  }

  default String getText() {
    return getCharsSequence().toString();
  }

  default int getTextLength() {
    return getCharsSequence().length();
  }

  int getLineCount();

  int getLineNumber(int offset);

  int getLineStartOffset(int line);

  int getLineEndOffset(int line);

  int getLineSeparatorLength(int line);
}
