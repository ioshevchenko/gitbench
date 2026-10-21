package gitbench.git;

import gitbench.testutil.TempRepo;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RollbackServiceTest {
  private final GitRunner runner = new GitRunner(() -> "git", GitCommandListener.NONE);
  private final RollbackService rollback = new RollbackService(runner);
  private final StatusService status = new StatusService(runner);

  private static TempRepo repoWithA() {
    TempRepo repo = TempRepo.init();
    repo.write("a.c", "a\n");
    repo.commitAll("init");
    return repo;
  }

  @Test
  void rollbackRestoresModified() throws Exception {
    TempRepo repo = repoWithA();
    repo.write("a.c", "changed\n");
    rollback.rollback(new GitRepository(repo.root()), List.of(new FileChange("a.c", null, ChangeKind.MODIFIED)), false);
    assertThat(Files.readString(repo.root().resolve("a.c"))).isEqualTo("a\n");
  }

  @Test
  void rollbackModifiedThatWasStaged() throws Exception {
    TempRepo repo = repoWithA();
    repo.write("a.c", "changed\n");
    repo.git("add", "a.c");
    GitRepository r = new GitRepository(repo.root());
    rollback.rollback(r, List.of(new FileChange("a.c", null, ChangeKind.MODIFIED)), false);
    assertThat(status.status(r)).isEmpty();
  }

  @Test
  void rollbackRestoresDeleted() throws Exception {
    TempRepo repo = repoWithA();
    Files.delete(repo.root().resolve("a.c"));
    rollback.rollback(new GitRepository(repo.root()), List.of(new FileChange("a.c", null, ChangeKind.DELETED)), false);
    assertThat(repo.root().resolve("a.c")).exists();
  }

  @Test
  void rollbackUnstagesAddedAndKeepsFile() throws Exception {
    TempRepo repo = repoWithA();
    repo.write("new.c", "n\n");
    repo.git("add", "new.c");
    GitRepository r = new GitRepository(repo.root());
    rollback.rollback(r, List.of(new FileChange("new.c", null, ChangeKind.ADDED)), false);
    assertThat(status.status(r)).containsExactly(new FileChange("new.c", null, ChangeKind.UNVERSIONED));
  }

  @Test
  void rollbackDeletesAddedWhenAsked() throws Exception {
    TempRepo repo = repoWithA();
    repo.write("new.c", "n\n");
    repo.git("add", "new.c");
    GitRepository r = new GitRepository(repo.root());
    rollback.rollback(r, List.of(new FileChange("new.c", null, ChangeKind.ADDED)), true);
    assertThat(repo.root().resolve("new.c")).doesNotExist();
    assertThat(status.status(r)).isEmpty();
  }

  @Test
  void rollbackRename() throws Exception {
    TempRepo repo = repoWithA();
    repo.git("mv", "a.c", "b.c");
    GitRepository r = new GitRepository(repo.root());
    rollback.rollback(r, List.of(new FileChange("b.c", "a.c", ChangeKind.RENAMED)), true);
    assertThat(repo.root().resolve("a.c")).exists();
    assertThat(status.status(r)).isEmpty();
  }
}
