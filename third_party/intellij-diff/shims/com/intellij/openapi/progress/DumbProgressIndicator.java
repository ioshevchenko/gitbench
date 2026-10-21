package com.intellij.openapi.progress;

/** Gitbench: an indicator that is never canceled. */
public final class DumbProgressIndicator implements ProgressIndicator {
  public static final DumbProgressIndicator INSTANCE = new DumbProgressIndicator();

  private DumbProgressIndicator() {
  }

  @Override
  public void checkCanceled() {
  }
}
