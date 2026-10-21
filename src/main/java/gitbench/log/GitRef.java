package gitbench.log;

/**
 * A branch, tag, or HEAD.
 *
 * @param name     the short name: {@code main}, {@code origin/main}, {@code v1.0}, {@code HEAD}
 * @param fullName the full name: {@code refs/heads/main}
 * @param hash     the full hash of the commit; for an annotated tag, the hash of the tagged commit
 * @param upstream the short name of the upstream branch, or {@code null}
 * @param current  true only for the checked out local branch
 */
public record GitRef(String name, String fullName, RefType type, String hash, String upstream, boolean current) {
}
