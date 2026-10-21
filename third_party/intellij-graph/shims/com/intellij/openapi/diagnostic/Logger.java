// Gitbench shim: a minimal replacement for the IntelliJ Platform class with the same name (Apache 2.0).
package com.intellij.openapi.diagnostic;

/** Writes through System.Logger. error() and a failed assertTrue() log at ERROR level and do not throw. */
public final class Logger {
  private final System.Logger delegate;

  private Logger(String name) {
    delegate = System.getLogger(name);
  }

  public static Logger getInstance(Class<?> type) {
    return new Logger(type.getName());
  }

  public static Logger getInstance(String name) {
    return new Logger(name);
  }

  public boolean isDebugEnabled() {
    return delegate.isLoggable(System.Logger.Level.DEBUG);
  }

  public void debug(String message) {
    delegate.log(System.Logger.Level.DEBUG, message);
  }

  public void info(String message) {
    delegate.log(System.Logger.Level.INFO, message);
  }

  public void warn(String message) {
    delegate.log(System.Logger.Level.WARNING, message);
  }

  public void warn(Throwable t) {
    delegate.log(System.Logger.Level.WARNING, String.valueOf(t.getMessage()), t);
  }

  public void error(String message) {
    delegate.log(System.Logger.Level.ERROR, message);
  }

  public void error(Throwable t) {
    delegate.log(System.Logger.Level.ERROR, String.valueOf(t.getMessage()), t);
  }

  public void error(String message, Throwable t) {
    delegate.log(System.Logger.Level.ERROR, message, t);
  }

  public boolean assertTrue(boolean value) {
    if (!value) {
      error("Assertion failed");
    }
    return value;
  }

  public boolean assertTrue(boolean value, Object message) {
    if (!value) {
      error("Assertion failed: " + message);
    }
    return value;
  }
}
