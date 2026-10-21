package gitbench.branch;

import gitbench.log.GitRef;

import java.util.List;

/** A node of the Branches tree: HEAD, the Local and Remote groups, remotes, folders, and branches. */
public sealed interface BranchTreeNode {
  /** "HEAD (Current Branch)" or "HEAD (detached at abc1234)". */
  record Head(String label) implements BranchTreeNode {
  }

  /** "Local" or "Remote". */
  record Group(String title, List<BranchTreeNode> children) implements BranchTreeNode {
  }

  /** A remote, such as {@code origin}, with its branches. */
  record Remote(String name, List<BranchTreeNode> children) implements BranchTreeNode {
  }

  /** A part of a branch name before a slash: {@code feature} in {@code feature/uart}. */
  record Folder(String name, List<BranchTreeNode> children) implements BranchTreeNode {
  }

  /**
   * A branch.
   *
   * @param label  the last part of the name, after the folders
   * @param counts incoming and outgoing commits, or {@code null}
   */
  record Branch(GitRef ref, String label, boolean favorite, AheadBehind.Counts counts) implements BranchTreeNode {
  }
}
