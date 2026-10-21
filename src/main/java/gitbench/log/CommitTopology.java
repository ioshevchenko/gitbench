package gitbench.log;

import com.intellij.vcs.log.graph.GraphCommit;

import java.util.List;

/**
 * All commits reachable from the refs, in {@code git log --topo-order} order, with parents as {@link CommitIndex} ids.
 */
public record CommitTopology(CommitIndex index, List<GraphCommit<Integer>> commits) {
}
