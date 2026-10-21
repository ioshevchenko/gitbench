package gitbench.git;

/**
 * Options of one commit.
 *
 * @param author {@code null}, or an author in the form {@code Name <mail>}
 */
public record CommitOptions(boolean amend, boolean signOff, String author) {
}
