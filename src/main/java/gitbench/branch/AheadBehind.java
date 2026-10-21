package gitbench.branch;

import gitbench.git.GitException;
import gitbench.git.GitRepository;
import gitbench.git.GitRunner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads how far each local branch is ahead of and behind its upstream. */
public final class AheadBehind {
  private static final Pattern AHEAD = Pattern.compile("ahead (\\d+)");
  private static final Pattern BEHIND = Pattern.compile("behind (\\d+)");

  /**
   * @param incoming commits in the upstream that the branch does not have (behind)
   * @param outgoing commits in the branch that the upstream does not have (ahead)
   */
  public record Counts(int incoming, int outgoing) {
  }

  private AheadBehind() {
  }

  /** Returns counts for the local branches that have an upstream and differ from it. */
  public static Map<String, Counts> load(GitRunner runner, GitRepository repo) throws GitException {
    String out = runner.runOk(repo.root(), List.of("for-each-ref", "--format=%(refname:short)%00%(upstream:track,nobracket)",
                                                   "refs/heads"), null);
    Map<String, Counts> counts = new HashMap<>();
    for (String line : out.split("\n")) {
      String[] f = line.split("\0", -1);
      if (f.length < 2 || f[1].isBlank()) {
        continue;
      }
      int ahead = number(AHEAD.matcher(f[1]));
      int behind = number(BEHIND.matcher(f[1]));
      if (ahead > 0 || behind > 0) {
        counts.put(f[0], new Counts(behind, ahead));
      }
    }
    return counts;
  }

  private static int number(Matcher matcher) {
    return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
  }
}
