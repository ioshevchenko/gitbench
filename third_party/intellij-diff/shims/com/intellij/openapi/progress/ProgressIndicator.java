package com.intellij.openapi.progress;

/** Gitbench: the part of the IntelliJ progress indicator that the diff code uses. */
public interface ProgressIndicator {
  void checkCanceled() throws ProcessCanceledException;

  default boolean isCanceled() {
    return false;
  }
}
