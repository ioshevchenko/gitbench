// Gitbench shim: a minimal replacement for the IntelliJ Platform class with the same name (Apache 2.0).
package com.intellij.openapi.progress;

public class ProcessCanceledException extends RuntimeException {
  public ProcessCanceledException() {
  }

  public ProcessCanceledException(Throwable cause) {
    super(cause);
  }
}
