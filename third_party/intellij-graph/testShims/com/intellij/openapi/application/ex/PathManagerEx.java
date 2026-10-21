// Gitbench shim: a minimal replacement for the IntelliJ Platform class with the same name (Apache 2.0).
package com.intellij.openapi.application.ex;

import java.io.File;

/** Maps the IntelliJ test data folder of the commit graph to the copy in third_party/intellij-graph/testData. */
public final class PathManagerEx {
  private static final String GRAPH_TEST_DATA = "platform/vcs-log/graph/testData/";

  private PathManagerEx() {
  }

  public static File findFileUnderCommunityHome(String relativePath) {
    String root = System.getProperty("gitbench.graphTestData");
    String rest = relativePath.startsWith(GRAPH_TEST_DATA) ? relativePath.substring(GRAPH_TEST_DATA.length()) : relativePath;
    return new File(root, rest);
  }
}
