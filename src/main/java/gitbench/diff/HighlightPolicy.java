package gitbench.diff;

import com.intellij.diff.comparison.InnerFragmentsPolicy;

/** The Highlight setting of the diff, as IntelliJ HighlightPolicy. */
public enum HighlightPolicy {
  BY_LINE("Lines"),
  BY_WORD("Words"),
  BY_WORD_SPLIT("Split changes"),
  BY_CHAR("Characters"),
  DO_NOT_HIGHLIGHT("None");

  private final String label;

  HighlightPolicy(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }

  boolean shouldCompare() {
    return this != DO_NOT_HIGHLIGHT;
  }

  boolean shouldSquash() {
    return this != BY_WORD_SPLIT;
  }

  InnerFragmentsPolicy fragmentsPolicy() {
    return switch (this) {
      case BY_WORD, BY_WORD_SPLIT -> InnerFragmentsPolicy.WORDS;
      case BY_CHAR -> InnerFragmentsPolicy.CHARS;
      case BY_LINE, DO_NOT_HIGHLIGHT -> InnerFragmentsPolicy.NONE;
    };
  }
}
