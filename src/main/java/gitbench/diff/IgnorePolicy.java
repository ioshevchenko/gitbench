package gitbench.diff;

import com.intellij.diff.comparison.ComparisonPolicy;

/** The Ignore setting of the diff, as IntelliJ IgnorePolicy without the language-specific values. */
public enum IgnorePolicy {
  DEFAULT("None"),
  TRIM_WHITESPACES("Trim whitespaces"),
  IGNORE_WHITESPACES("Ignore whitespaces"),
  IGNORE_WHITESPACES_CHUNKS("Ignore whitespaces and empty lines");

  private final String label;

  IgnorePolicy(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }

  public ComparisonPolicy comparisonPolicy() {
    return switch (this) {
      case DEFAULT -> ComparisonPolicy.DEFAULT;
      case TRIM_WHITESPACES -> ComparisonPolicy.TRIM_WHITESPACES;
      case IGNORE_WHITESPACES, IGNORE_WHITESPACES_CHUNKS -> ComparisonPolicy.IGNORE_WHITESPACES;
    };
  }

  boolean shouldTrimChunks() {
    return this == IGNORE_WHITESPACES_CHUNKS;
  }
}
