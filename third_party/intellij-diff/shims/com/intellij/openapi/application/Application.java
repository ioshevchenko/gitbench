package com.intellij.openapi.application;

import com.intellij.diff.comparison.ComparisonManager;
import com.intellij.diff.comparison.ComparisonManagerImpl;

/** Gitbench: a service registry with the comparison manager only. */
public final class Application {
  private final ComparisonManagerImpl comparisonManager = new ComparisonManagerImpl();

  Application() {
  }

  public <T> T getService(Class<T> serviceClass) {
    if (serviceClass == ComparisonManager.class || serviceClass == ComparisonManagerImpl.class) {
      return serviceClass.cast(comparisonManager);
    }
    throw new IllegalArgumentException("Gitbench has no service " + serviceClass.getName());
  }
}
