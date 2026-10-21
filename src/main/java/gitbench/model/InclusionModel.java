package gitbench.model;

import gitbench.git.ChangeKind;
import gitbench.git.FileChange;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Remembers which files have a checked box in the Commit view. The key is the file path, so the choice
 * survives a refresh. New tracked changes are checked, new unversioned files are not, as in IntelliJ.
 */
public final class InclusionModel {
  public enum State { ON, OFF, PARTIAL }

  private final Map<String, Boolean> included = new HashMap<>();
  private List<FileChange> current = List.of();

  public void setIncluded(Collection<FileChange> changes, boolean on) {
    for (FileChange change : changes) {
      included.put(change.path(), on);
    }
  }

  public boolean isIncluded(FileChange change) {
    return included.getOrDefault(change.path(), false);
  }

  public State stateOf(ChangesTreeNode node) {
    List<FileChange> files = new ArrayList<>();
    collect(node, files);
    long on = files.stream().filter(this::isIncluded).count();
    if (on == 0) {
      return State.OFF;
    }
    return on == files.size() ? State.ON : State.PARTIAL;
  }

  /** Returns the checked files in the order of the last status. */
  public List<FileChange> included() {
    return current.stream().filter(this::isIncluded).toList();
  }

  /** Takes a new status. Drops gone files, keeps the choice for the remaining ones, applies defaults to new ones. */
  public void retainOnly(Collection<FileChange> changes) {
    Map<String, Boolean> kept = new HashMap<>();
    for (FileChange change : changes) {
      kept.put(change.path(), included.getOrDefault(change.path(), change.kind() != ChangeKind.UNVERSIONED));
    }
    included.clear();
    included.putAll(kept);
    current = List.copyOf(changes);
  }

  public static void collect(ChangesTreeNode node, List<FileChange> out) {
    if (node instanceof ChangesTreeNode.File file) {
      out.add(file.change());
    }
    else if (node instanceof ChangesTreeNode.Dir dir) {
      dir.children().forEach(child -> collect(child, out));
    }
    else if (node instanceof ChangesTreeNode.Root root) {
      root.children().forEach(child -> collect(child, out));
    }
  }
}
