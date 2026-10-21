package gitbench.model;

import gitbench.git.FileChange;

import java.util.List;

/** A node of the Commit view tree. */
public sealed interface ChangesTreeNode {
  /**
   * A top node: "Changes" or "Unversioned Files".
   *
   * @param fileCount the number of files under the node
   */
  record Root(String title, List<ChangesTreeNode> children, int fileCount, boolean unversioned) implements ChangesTreeNode {
  }

  /**
   * A folder. Folders with one sub-folder and no files merge into one node.
   *
   * @param path  the full folder path from the repository root
   * @param label the text to show, for example {@code Core/Src} for a merged folder
   */
  record Dir(String path, String label, List<ChangesTreeNode> children) implements ChangesTreeNode {
  }

  record File(FileChange change) implements ChangesTreeNode {
  }
}
