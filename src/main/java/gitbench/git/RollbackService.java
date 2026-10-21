package gitbench.git;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Reverts local changes to the HEAD state. */
public final class RollbackService {
  private final GitRunner runner;

  public RollbackService(GitRunner runner) {
    this.runner = runner;
  }

  /**
   * Reverts {@code changes}. An added file leaves the index. When {@code deleteAddedFiles} is true, the added file
   * is also deleted from the disk. A rename is reverted as an added new path plus a deleted old path.
   */
  public void rollback(GitRepository repo, List<FileChange> changes, boolean deleteAddedFiles) throws GitException {
    List<String> restore = new ArrayList<>();
    List<String> added = new ArrayList<>();
    for (FileChange change : changes) {
      switch (change.kind()) {
        case MODIFIED, DELETED, CONFLICT -> restore.add(change.path());
        case ADDED -> added.add(change.path());
        case RENAMED -> {
          added.add(change.path());
          restore.add(change.origPath());
        }
        case UNVERSIONED -> {
          // An unversioned file has no version to go back to.
        }
      }
    }
    if (!added.isEmpty()) {
      CommitService.runPathspec(runner, repo, List.of("rm", "--cached", "-q", "-f"), added);
      if (deleteAddedFiles) {
        for (String path : added) {
          try {
            Files.deleteIfExists(repo.root().resolve(path));
          }
          catch (IOException e) {
            throw new GitException("Cannot delete " + path + ": " + e.getMessage(), e);
          }
        }
      }
    }
    if (!restore.isEmpty()) {
      CommitService.runPathspec(runner, repo, List.of("restore", "--source=HEAD", "--staged", "--worktree"), restore);
    }
  }
}
