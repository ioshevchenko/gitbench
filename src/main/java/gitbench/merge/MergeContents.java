package gitbench.merge;

import gitbench.diff.LoadedContent;

/**
 * The three texts of a conflicted file in the order of the merge window: Yours, Base, Theirs. The line separator is
 * the one the result is saved with.
 */
public record MergeContents(LoadedContent yours, LoadedContent base, LoadedContent theirs, String lineSeparator,
                            java.nio.charset.Charset charset, boolean bom) {
  public boolean binary() {
    return yours.binary() || base.binary() || theirs.binary();
  }
}
