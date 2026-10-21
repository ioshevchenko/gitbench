package gitbench.model;

import gitbench.git.ChangeKind;
import gitbench.git.FileChange;
import gitbench.model.ChangesTreeNode.Dir;
import gitbench.model.ChangesTreeNode.File;
import gitbench.model.ChangesTreeNode.Root;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Builds the Commit view tree from the local changes, the way the IntelliJ Commit window groups them. */
public final class ChangesTreeBuilder {
  public static final String CHANGES = "Changes";
  public static final String UNVERSIONED_FILES = "Unversioned Files";
  public static final String MERGE_CONFLICTS = "Merge Conflicts";

  private static final Comparator<FileChange> BY_FILE_NAME =
    Comparator.comparing(FileChange::fileName, String.CASE_INSENSITIVE_ORDER).thenComparing(FileChange::path);

  private ChangesTreeBuilder() {
  }

  /**
   * Returns the "Changes" root, the "Unversioned Files" root when there are unversioned files, and before them the
   * "Merge Conflicts" root when there are conflicted files, as IntelliJ shows them.
   */
  public static List<Root> build(List<FileChange> changes, boolean groupByDirectory) {
    List<FileChange> conflicts = changes.stream().filter(c -> c.kind() == ChangeKind.CONFLICT).toList();
    List<FileChange> tracked = changes.stream().filter(c -> c.kind() != ChangeKind.UNVERSIONED && c.kind() != ChangeKind.CONFLICT).toList();
    List<FileChange> unversioned = changes.stream().filter(c -> c.kind() == ChangeKind.UNVERSIONED).toList();
    List<Root> roots = new ArrayList<>();
    if (!conflicts.isEmpty()) {
      roots.add(new Root(MERGE_CONFLICTS, children(conflicts, groupByDirectory), conflicts.size(), false));
    }
    roots.add(new Root(CHANGES, children(tracked, groupByDirectory), tracked.size(), false));
    if (!unversioned.isEmpty()) {
      roots.add(new Root(UNVERSIONED_FILES, children(unversioned, groupByDirectory), unversioned.size(), true));
    }
    return roots;
  }

  private static List<ChangesTreeNode> children(List<FileChange> changes, boolean groupByDirectory) {
    if (!groupByDirectory) {
      return changes.stream().sorted(BY_FILE_NAME).<ChangesTreeNode>map(File::new).toList();
    }
    Folder top = new Folder("");
    for (FileChange change : changes) {
      Folder folder = top;
      String parent = change.parentPath();
      if (!parent.isEmpty()) {
        for (String part : parent.split("/")) {
          String path = folder.path.isEmpty() ? part : folder.path + "/" + part;
          folder = folder.folders.computeIfAbsent(part.toLowerCase(java.util.Locale.ROOT) + "\0" + part, k -> new Folder(path));
        }
      }
      folder.files.add(change);
    }
    return top.nodes();
  }

  /** A mutable folder used while the tree is built. Folder keys sort case-insensitively. */
  private static final class Folder {
    final String path;
    final Map<String, Folder> folders = new TreeMap<>();
    final List<FileChange> files = new ArrayList<>();

    Folder(String path) {
      this.path = path;
    }

    String name() {
      return path.substring(path.lastIndexOf('/') + 1);
    }

    List<ChangesTreeNode> nodes() {
      List<ChangesTreeNode> nodes = new ArrayList<>();
      for (Folder child : folders.values()) {
        nodes.add(child.toDir(child.name()));
      }
      files.stream().sorted(BY_FILE_NAME).map(File::new).forEach(nodes::add);
      return nodes;
    }

    Dir toDir(String label) {
      if (files.isEmpty() && folders.size() == 1) {
        Folder only = folders.values().iterator().next();
        return only.toDir(label + "/" + only.name());
      }
      return new Dir(path, label, nodes());
    }
  }
}
