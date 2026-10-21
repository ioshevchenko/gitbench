package gitbench.diff;

import gitbench.git.GitRepository;

/** What one diff tab compares: two sides of one file in a repository. The path is relative to the repository root. */
public record DiffRequest(GitRepository repository, String path, DiffSide left, DiffSide right) {
  /** The file name, as the IntelliJ diff tab title. */
  public String title() {
    int slash = path.lastIndexOf('/');
    return slash < 0 ? path : path.substring(slash + 1);
  }
}
