package gitbench.git;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Finds the git repository that contains a path. */
public final class RepositoryLocator {
  private final GitRunner runner;

  public RepositoryLocator(GitRunner runner) {
    this.runner = runner;
  }

  public Optional<GitRepository> find(Path anyPathInside) throws GitException {
    Path dir = Files.isDirectory(anyPathInside) ? anyPathInside : anyPathInside.getParent();
    if (dir == null || !Files.isDirectory(dir)) {
      return Optional.empty();
    }
    GitResult result = runner.run(dir, List.of("rev-parse", "--show-toplevel"), null);
    if (!result.ok()) {
      return Optional.empty();
    }
    try {
      return Optional.of(new GitRepository(Path.of(result.stdoutText().trim()).toRealPath()));
    }
    catch (java.io.IOException e) {
      return Optional.empty();
    }
  }
}
