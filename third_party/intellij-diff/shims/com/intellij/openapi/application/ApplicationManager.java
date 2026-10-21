package com.intellij.openapi.application;

/** Gitbench: gives the one service the ported diff code asks for. */
public final class ApplicationManager {
  private static final Application APPLICATION = new Application();

  private ApplicationManager() {
  }

  public static Application getApplication() {
    return APPLICATION;
  }
}
