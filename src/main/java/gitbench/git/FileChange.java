package gitbench.git;

/**
 * One changed file.
 *
 * @param path     the path relative to the repository root, with {@code /} separators
 * @param origPath the old path of a rename, or {@code null}
 */
public record FileChange(String path, String origPath, ChangeKind kind) {
  public String fileName() {
    int slash = path.lastIndexOf('/');
    return slash < 0 ? path : path.substring(slash + 1);
  }

  public String parentPath() {
    int slash = path.lastIndexOf('/');
    return slash < 0 ? "" : path.substring(0, slash);
  }
}
