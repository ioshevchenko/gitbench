package gitbench.log;

import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.graph.RowInfo;
import com.intellij.vcs.log.graph.VisibleGraph;
import com.intellij.vcs.log.graph.impl.facade.PermanentGraphImpl;
import com.intellij.vcs.log.graph.impl.print.GraphColorGetterByHeadFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The rows of the Git Log: the IntelliJ commit graph built from the topology, with the refs of each commit.
 * The permanent graph is built once; filters only create a new visible graph from it.
 */
public final class LogModel {
  private final CommitTopology topology;
  private final Map<Integer, List<GitRef>> refsByCommit;
  private final PermanentGraph<Integer> permanent;
  private final VisibleGraph<Integer> visible;
  private final boolean intelliSort;
  private java.util.function.Predicate<Integer> currentBranchCondition;

  private LogModel(CommitTopology topology, Map<Integer, List<GitRef>> refsByCommit, PermanentGraph<Integer> permanent,
                   VisibleGraph<Integer> visible, boolean intelliSort) {
    this.topology = topology;
    this.refsByCommit = refsByCommit;
    this.permanent = permanent;
    this.visible = visible;
    this.intelliSort = intelliSort;
  }

  /** Builds the graph. IntelliSort on is the IntelliJ default "Standard" sort: incoming commits below the merge. */
  public static LogModel build(CommitTopology topology, List<GitRef> refs, boolean intelliSort) {
    Map<Integer, List<GitRef>> refsByCommit = new HashMap<>();
    Set<Integer> branchHeads = new HashSet<>();
    for (GitRef ref : refs) {
      int index = topology.index().indexOf(ref.hash());
      if (index < 0) {
        continue;
      }
      refsByCommit.computeIfAbsent(index, k -> new ArrayList<>()).add(ref);
      if (ref.type() != RefType.TAG) {
        branchHeads.add(index);
      }
    }
    refsByCommit.values().forEach(list -> list.sort(HeadRefColors.PRIORITY));
    if (topology.commits().isEmpty()) {
      return new LogModel(topology, refsByCommit, null, null, intelliSort);
    }
    HeadRefColors colors = new HeadRefColors(id -> refsByCommit.getOrDefault(id, List.of()));
    Comparator<Integer> headOrder = Comparator.comparing(
      (Integer id) -> refsByCommit.getOrDefault(id, List.of()).stream().findFirst().orElse(null),
      Comparator.nullsLast(HeadRefColors.PRIORITY));
    PermanentGraph<Integer> permanent = PermanentGraphImpl.newInstance(
      topology.commits(), new GraphColorGetterByHeadFactory<>(colors), headOrder, branchHeads);
    return new LogModel(topology, refsByCommit, permanent, createVisible(permanent, intelliSort, null, null), intelliSort);
  }

  /**
   * Returns a model that shows only the branches with the given heads and only the matched commits.
   * A {@code null} argument means no restriction.
   */
  public LogModel filtered(Set<Integer> visibleHeads, Set<Integer> matched) {
    if (permanent == null) {
      return this;
    }
    return new LogModel(topology, refsByCommit, permanent, createVisible(permanent, intelliSort, visibleHeads, matched), intelliSort);
  }

  private static VisibleGraph<Integer> createVisible(PermanentGraph<Integer> permanent, boolean intelliSort,
                                                     Set<Integer> heads, Set<Integer> matched) {
    PermanentGraph.SortType sort = intelliSort ? PermanentGraph.SortType.Bek : PermanentGraph.SortType.Normal;
    return permanent.createVisibleGraph(new PermanentGraph.Options.Base(sort), heads, matched);
  }

  public int rowCount() {
    return visible == null ? 0 : visible.getVisibleCommitCount();
  }

  public LogRow row(int row) {
    RowInfo<Integer> info = visible.getRowInfo(row);
    int commit = info.getCommit();
    return new LogRow(topology.index().hashOf(commit), commit, info.getPrintElements(), refsByCommit.getOrDefault(commit, List.of()));
  }

  /** Returns the row of the commit, or -1 when the commit is not visible. */
  public int rowOf(String hash) {
    int index = topology.index().indexOf(hash);
    if (index < 0 || visible == null) {
      return -1;
    }
    Integer row = visible.getVisibleRowIndex(index);
    return row == null ? -1 : row;
  }

  /** Returns the {@link CommitIndex} id of the commit, or -1. Filters take these ids. */
  public int commitIndexOf(String hash) {
    return topology.index().indexOf(hash);
  }

  /**
   * True when the commit is reachable from HEAD. The log paints other commits paler, as the IntelliJ
   * "current branch" highlighter does. Without HEAD every commit counts as in the branch.
   */
  public boolean inCurrentBranch(int commitIndex) {
    if (permanent == null) {
      return true;
    }
    if (currentBranchCondition == null) {
      Integer head = refsByCommit.entrySet().stream()
        .filter(e -> e.getValue().stream().anyMatch(ref -> ref.type() == RefType.HEAD))
        .map(Map.Entry::getKey).findFirst().orElse(null);
      currentBranchCondition = head == null ? id -> true : permanent.getContainedInBranchCondition(List.of(head));
    }
    return currentBranchCondition.test(commitIndex);
  }

  /** The number of graph columns IntelliJ suggests for this graph. */
  public int recommendedWidth() {
    return visible == null ? 0 : visible.getRecommendedWidth();
  }

  public boolean intelliSort() {
    return intelliSort;
  }

  public CommitTopology topology() {
    return topology;
  }
}
