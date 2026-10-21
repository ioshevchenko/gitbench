package gitbench.log;

import java.time.Instant;
import java.util.List;

/** The text data of one commit, shown in the log table and in the details panel. */
public record CommitDetails(String hash, List<String> parents, String authorName, String authorEmail, Instant authorTime,
                            String committerName, Instant commitTime, String subject, String fullMessage) {
}
