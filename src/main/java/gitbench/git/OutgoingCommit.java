package gitbench.git;

import java.time.Instant;

/** A commit that a push sends to the remote. */
public record OutgoingCommit(String shortHash, String subject, String author, Instant date) {
}
