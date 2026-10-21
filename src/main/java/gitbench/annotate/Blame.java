package gitbench.annotate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The commit of each line of a file. A line that is not committed has the hash of zeros. */
public record Blame(List<String> lineHashes, Map<String, BlameCommit> commits) {
  static final String UNCOMMITTED = "0000000000000000000000000000000000000000";

  public Blame {
    lineHashes = List.copyOf(lineHashes);
    commits = Map.copyOf(commits);
  }

  /** The commit of the line; empty for a line that is not committed and for a line outside the file. */
  public Optional<BlameCommit> commitAt(int line) {
    if (line < 0 || line >= lineHashes.size() || UNCOMMITTED.equals(lineHashes.get(line))) {
      return Optional.empty();
    }
    return Optional.ofNullable(commits.get(lineHashes.get(line)));
  }
}
