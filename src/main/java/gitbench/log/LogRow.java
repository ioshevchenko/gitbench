package gitbench.log;

import com.intellij.vcs.log.graph.PrintElement;

import java.util.Collection;
import java.util.List;

/**
 * One row of the log table.
 *
 * @param commitIndex   the {@link CommitIndex} id of the commit
 * @param printElements the graph nodes and edges to paint in this row
 * @param refs          the refs that point at this commit, best first
 */
public record LogRow(String hash, int commitIndex, Collection<? extends PrintElement> printElements, List<GitRef> refs) {
}
