package gitbench.git;

import java.util.List;

/** Reads the local changes of a repository. */
public final class StatusService {
  private final GitRunner runner;

  public StatusService(GitRunner runner) {
    this.runner = runner;
  }

  public List<FileChange> status(GitRepository repo) throws GitException {
    GitResult result = runner.run(repo.root(), List.of("status", "--porcelain=v2", "-z", "--untracked-files=all"), null);
    if (!result.ok()) {
      throw GitException.fromResult("git status", result);
    }
    return StatusParser.parse(result.stdout());
  }
}
