package gitbench.diff;

import java.nio.file.Path;

/** One side of a diff: a file at a revision, the file on disk, or nothing. */
public sealed interface DiffSide {
  /** The file at a revision. The revision is a hash or a name such as HEAD; the path is relative to the repository root. */
  record Revision(String revision, String path) implements DiffSide {
  }

  /** The file in the working tree. Only this side can be edited. */
  record WorkingFile(Path absolutePath) implements DiffSide {
  }

  /** No file on this side, for example for an added or a deleted file. */
  record Empty(String label) implements DiffSide {
  }

  default boolean editable() {
    return this instanceof WorkingFile;
  }
}
