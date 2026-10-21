package gitbench.log;

import gitbench.git.GitException;
import gitbench.git.GitRepository;
import gitbench.git.GitRunner;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Reads subjects, authors, dates, and messages for a batch of commits. The hashes go through stdin. */
public final class DetailsLoader {
  private static final String FORMAT = "--format=%H%x00%P%x00%an%x00%ae%x00%at%x00%cn%x00%ct%x00%s%x00%B%x01";

  private final GitRunner runner;

  public DetailsLoader(GitRunner runner) {
    this.runner = runner;
  }

  public Map<String, CommitDetails> load(GitRepository repo, List<String> hashes) throws GitException {
    Map<String, CommitDetails> result = new HashMap<>();
    if (hashes.isEmpty()) {
      return result;
    }
    String stdin = String.join("\n", hashes) + "\n";
    String out = runner.runOk(repo.root(), List.of("log", "--no-walk=unsorted", "--stdin", FORMAT),
                              stdin.getBytes(StandardCharsets.UTF_8));
    for (String record : out.split("\u0001")) {
      String trimmed = record.startsWith("\n") ? record.substring(1) : record;
      String[] f = trimmed.split("\0", 9);
      if (f.length < 9) {
        continue;
      }
      List<String> parents = f[1].isEmpty() ? List.of() : Arrays.asList(f[1].split(" "));
      result.put(f[0], new CommitDetails(f[0], List.copyOf(parents), f[2], f[3], Instant.ofEpochSecond(Long.parseLong(f[4])),
                                         f[5], Instant.ofEpochSecond(Long.parseLong(f[6])), f[7], f[8].strip()));
    }
    return result;
  }
}
