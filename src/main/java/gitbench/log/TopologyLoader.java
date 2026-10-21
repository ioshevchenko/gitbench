package gitbench.log;

import com.intellij.vcs.log.graph.GraphCommit;
import com.intellij.vcs.log.graph.GraphCommitImpl;
import gitbench.git.GitException;
import gitbench.git.GitRepository;
import gitbench.git.GitRunner;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Reads hashes, parents, and commit times of all commits. It is one cheap git call even for big histories. */
public final class TopologyLoader {
  private final GitRunner runner;

  public TopologyLoader(GitRunner runner) {
    this.runner = runner;
  }

  public CommitTopology load(GitRepository repo, List<GitRef> refs) throws GitException {
    CommitIndex index = new CommitIndex();
    Set<String> starts = new LinkedHashSet<>();
    refs.forEach(ref -> starts.add(ref.hash()));
    if (starts.isEmpty()) {
      return new CommitTopology(index, List.of());
    }
    String stdin = String.join("\n", starts) + "\n";
    String out = runner.runOk(repo.root(), List.of("log", "--topo-order", "--format=%H %P %ct", "--stdin"),
                              stdin.getBytes(StandardCharsets.UTF_8));
    List<String[]> lines = new ArrayList<>();
    for (String line : out.split("\n")) {
      if (!line.isEmpty()) {
        String[] tokens = line.split(" ");
        index.add(tokens[0]);
        lines.add(tokens);
      }
    }
    List<GraphCommit<Integer>> commits = new ArrayList<>(lines.size());
    for (String[] tokens : lines) {
      List<Integer> parents = new ArrayList<>(tokens.length - 2);
      for (int i = 1; i < tokens.length - 1; i++) {
        // A root commit has an empty %P, which leaves an empty token between two spaces.
        if (!tokens[i].isEmpty()) {
          parents.add(index.add(tokens[i]));
        }
      }
      long time = Long.parseLong(tokens[tokens.length - 1]);
      commits.add(GraphCommitImpl.createIntCommit(index.indexOf(tokens[0]), parents, time));
    }
    return new CommitTopology(index, commits);
  }
}
