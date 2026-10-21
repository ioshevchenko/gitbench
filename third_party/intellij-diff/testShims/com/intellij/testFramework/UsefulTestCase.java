package com.intellij.testFramework;

import org.junit.Assert;

import java.util.ArrayList;
import java.util.Collection;

/** Gitbench: the static helpers of the IntelliJ test case that the comparison tests use. */
public final class UsefulTestCase {
  public static final boolean IS_UNDER_TEAMCITY = false;

  private UsefulTestCase() {
  }

  public static String getTestName(String name, boolean lowercaseFirstLetter) {
    String result = name != null && name.startsWith("test") ? name.substring(4) : String.valueOf(name);
    if (lowercaseFirstLetter && !result.isEmpty() && !(result.length() > 1 && Character.isUpperCase(result.charAt(1)))) {
      result = Character.toLowerCase(result.charAt(0)) + result.substring(1);
    }
    return result;
  }

  public static void assertOrderedEquals(String message, Collection<?> actual, Collection<?> expected) {
    Assert.assertEquals(message, new ArrayList<>(expected), new ArrayList<>(actual));
  }
}
