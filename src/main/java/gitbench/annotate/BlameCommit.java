package gitbench.annotate;

import java.time.Instant;

/**
 * A commit of a blame, from its header in {@code git blame --porcelain}. {@code filename} is the path of the file in
 * this commit; {@code previousHash} and {@code previousFilename} name the parent version, null for the first commit of
 * the file.
 */
public record BlameCommit(String hash, String author, String authorMail, Instant authorTime, String summary, String filename,
                          String previousHash, String previousFilename) {
}
