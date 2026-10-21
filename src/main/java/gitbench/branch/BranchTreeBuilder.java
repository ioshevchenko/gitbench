package gitbench.branch;

import gitbench.branch.BranchTreeNode.Branch;
import gitbench.branch.BranchTreeNode.Folder;
import gitbench.branch.BranchTreeNode.Group;
import gitbench.branch.BranchTreeNode.Head;
import gitbench.branch.BranchTreeNode.Remote;
import gitbench.git.GitException;
import gitbench.git.GitRepository;
import gitbench.git.GitResult;
import gitbench.git.GitRunner;
import gitbench.log.GitRef;
import gitbench.log.RefType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Builds the Branches tree: HEAD, Local, Remote. Names with slashes become folders. On each level favorite branches
 * come first, then folders, then the other branches, by name without case.
 */
public final class BranchTreeBuilder {
  private BranchTreeBuilder() {
  }

  /**
   * @param favorites   favorite names: short local names and {@code remote/branch} names
   * @param filterText  speed search text, or null
   * @param onlyMine    keep only {@code myBranches}
   */
  public static List<BranchTreeNode> build(List<GitRef> refs, Set<String> favorites, Map<String, AheadBehind.Counts> counts,
                                           String filterText, boolean onlyMine, Set<String> myBranches) {
    String filter = filterText == null || filterText.isBlank() ? null : filterText.trim().toLowerCase(Locale.ROOT);
    List<BranchTreeNode> roots = new ArrayList<>();
    boolean hasCurrent = refs.stream().anyMatch(GitRef::current);
    refs.stream().filter(r -> r.type() == RefType.HEAD).findFirst().ifPresent(head -> roots.add(new Head(
      hasCurrent ? "HEAD (Current Branch)" : "HEAD (detached at " + head.hash().substring(0, Math.min(7, head.hash().length())) + ")")));

    List<GitRef> locals = refs.stream()
      .filter(r -> r.type() == RefType.LOCAL_BRANCH)
      .filter(r -> matches(r, filter))
      .filter(r -> !onlyMine || myBranches.contains(r.name()))
      .toList();
    if (!locals.isEmpty()) {
      Node top = new Node();
      locals.forEach(ref -> top.add(ref.name().split("/"), 0, ref));
      roots.add(new Group("Local", top.toNodes(favorites, counts)));
    }

    List<GitRef> remotes = refs.stream()
      .filter(r -> r.type() == RefType.REMOTE_BRANCH)
      .filter(r -> matches(r, filter))
      .filter(r -> !onlyMine)
      .toList();
    if (!remotes.isEmpty()) {
      Map<String, Node> byRemote = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
      for (GitRef ref : remotes) {
        int slash = ref.name().indexOf('/');
        String remote = slash < 0 ? ref.name() : ref.name().substring(0, slash);
        String rest = slash < 0 ? ref.name() : ref.name().substring(slash + 1);
        byRemote.computeIfAbsent(remote, k -> new Node()).add(rest.split("/"), 0, ref);
      }
      List<BranchTreeNode> remoteNodes = new ArrayList<>();
      byRemote.forEach((name, node) -> remoteNodes.add(new Remote(name, node.toNodes(favorites, counts))));
      roots.add(new Group("Remote", remoteNodes));
    }
    return roots;
  }

  /** The text a tree row shows for a node. */
  public static String labelOf(BranchTreeNode node) {
    if (node instanceof Head head) {
      return head.label();
    }
    if (node instanceof Group group) {
      return group.title();
    }
    if (node instanceof Remote remote) {
      return remote.name();
    }
    if (node instanceof Folder folder) {
      return folder.name();
    }
    return ((Branch)node).label();
  }

  /** The local branches whose last commit has the author email of {@code git config user.email}. */
  public static Set<String> myBranches(GitRunner runner, GitRepository repo) throws GitException {
    GitResult email = runner.run(repo.root(), List.of("config", "--get", "user.email"), null);
    if (!email.ok()) {
      return Set.of();
    }
    String me = email.stdoutText().trim().toLowerCase(Locale.ROOT);
    String out = runner.runOk(repo.root(), List.of("for-each-ref", "--format=%(refname:short)%00%(authoremail)", "refs/heads"), null);
    Set<String> mine = new LinkedHashSet<>();
    for (String line : out.split("\n")) {
      String[] f = line.split("\0", -1);
      if (f.length == 2 && f[1].replace("<", "").replace(">", "").trim().toLowerCase(Locale.ROOT).equals(me)) {
        mine.add(f[0]);
      }
    }
    return mine;
  }

  private static boolean matches(GitRef ref, String filter) {
    return filter == null || ref.name().toLowerCase(Locale.ROOT).contains(filter);
  }

  /** A mutable folder used while the tree is built. */
  private static final class Node {
    final Map<String, Node> folders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    final List<GitRef> branches = new ArrayList<>();
    final Map<GitRef, String> labels = new java.util.HashMap<>();

    void add(String[] parts, int index, GitRef ref) {
      if (index == parts.length - 1) {
        branches.add(ref);
        labels.put(ref, parts[index]);
        return;
      }
      folders.computeIfAbsent(parts[index], k -> new Node()).add(parts, index + 1, ref);
    }

    List<BranchTreeNode> toNodes(Set<String> favorites, Map<String, AheadBehind.Counts> counts) {
      Comparator<GitRef> byLabel = Comparator.comparing(labels::get, String.CASE_INSENSITIVE_ORDER);
      List<BranchTreeNode> result = new ArrayList<>();
      branches.stream().filter(r -> favorites.contains(r.name())).sorted(byLabel)
        .forEach(r -> result.add(new Branch(r, labels.get(r), true, counts.get(r.name()))));
      folders.forEach((name, node) -> result.add(new Folder(name, node.toNodes(favorites, counts))));
      branches.stream().filter(r -> !favorites.contains(r.name())).sorted(byLabel)
        .forEach(r -> result.add(new Branch(r, labels.get(r), false, counts.get(r.name()))));
      return result;
    }
  }
}
