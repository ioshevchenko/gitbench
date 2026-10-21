package gitbench.log;

import gitbench.git.ChangeKind;
import gitbench.git.FileChange;
import gitbench.git.GitCommandListener;
import gitbench.git.GitRepository;
import gitbench.git.GitRunner;
import gitbench.git.StatusService;
import gitbench.testutil.TempRepo;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

class CommitOperationsTest {
  private final GitRunner runner = new GitRunner(() -> "git", GitCommandListener.NONE);
  private final CommitOperations ops = new CommitOperations(runner);
  private final StatusService status = new StatusService(runner);

  /** main: base → m1; feature (from base): f1 changes f.c. */
  private static TempRepo repo() {
    TempRepo repo = TempRepo.init();
    repo.write("a.c", "base\n");
    repo.commitAll("base");
    repo.git("switch", "-q", "-c", "feature");
    repo.write("f.c", "feature\n");
    repo.commitAll("f1");
    repo.git("switch", "-q", "main");
    repo.write("m.c", "main\n");
    repo.commitAll("m1");
    return repo;
  }

  private static String rev(TempRepo repo, String rev) {
    return repo.git("rev-parse", rev).trim();
  }

  @Test
  void cherryPickAppliesCommit() throws Exception {
    TempRepo repo = repo();
    OperationResult result = ops.cherryPick(new GitRepository(repo.root()), rev(repo, "feature"));
    assertThat(result.ok()).isTrue();
    assertThat(repo.git("log", "-1", "--format=%s").trim()).isEqualTo("f1");
    assertThat(repo.root().resolve("f.c")).exists();
  }

  @Test
  void cherryPickConflictReported() throws Exception {
    TempRepo repo = repo();
    repo.git("switch", "-q", "feature");
    repo.write("a.c", "feature change\n");
    repo.commitAll("feature edits a");
    repo.git("switch", "-q", "main");
    repo.write("a.c", "main change\n");
    repo.commitAll("main edits a");
    GitRepository r = new GitRepository(repo.root());
    OperationResult result = ops.cherryPick(r, rev(repo, "feature"));
    assertThat(result.ok()).isFalse();
    assertThat(result.conflicts()).isTrue();
    assertThat(result.message()).contains("Conflicts during cherry-pick");
    assertThat(status.status(r)).contains(new FileChange("a.c", null, ChangeKind.CONFLICT));
  }

  @Test
  void revertCreatesRevertCommit() throws Exception {
    TempRepo repo = repo();
    OperationResult result = ops.revert(new GitRepository(repo.root()), rev(repo, "HEAD"));
    assertThat(result.ok()).isTrue();
    assertThat(repo.git("log", "-1", "--format=%s").trim()).startsWith("Revert \"m1\"");
    assertThat(repo.root().resolve("m.c")).doesNotExist();
  }

  @Test
  void resetSoftKeepsChangesStaged() throws Exception {
    TempRepo repo = repo();
    GitRepository r = new GitRepository(repo.root());
    assertThat(ops.reset(r, rev(repo, "HEAD~1"), ResetMode.SOFT).ok()).isTrue();
    assertThat(status.status(r)).containsExactly(new FileChange("m.c", null, ChangeKind.ADDED));
  }

  @Test
  void resetHardDropsChanges() throws Exception {
    TempRepo repo = repo();
    GitRepository r = new GitRepository(repo.root());
    repo.write("a.c", "dirty\n");
    assertThat(ops.reset(r, rev(repo, "HEAD~1"), ResetMode.HARD).ok()).isTrue();
    assertThat(status.status(r)).isEmpty();
    assertThat(Files.readString(repo.root().resolve("a.c"))).isEqualTo("base\n");
  }

  @Test
  void checkoutRevisionDetachesHead() throws Exception {
    TempRepo repo = repo();
    String base = rev(repo, "HEAD~1");
    assertThat(ops.checkoutRevision(new GitRepository(repo.root()), base).ok()).isTrue();
    assertThat(rev(repo, "HEAD")).isEqualTo(base);
    assertThat(repo.git("branch", "--show-current").trim()).isEmpty();
  }

  @Test
  void createBranchAtCommitAndCheckout() throws Exception {
    TempRepo repo = repo();
    String base = rev(repo, "HEAD~1");
    assertThat(ops.createBranch(new GitRepository(repo.root()), "hotfix/uart", base, true).ok()).isTrue();
    assertThat(repo.git("branch", "--show-current").trim()).isEqualTo("hotfix/uart");
    assertThat(rev(repo, "HEAD")).isEqualTo(base);
  }

  @Test
  void createBranchOverwritesWhenForced() throws Exception {
    TempRepo repo = repo();
    String base = rev(repo, "HEAD~1");
    assertThat(ops.createBranch(new GitRepository(repo.root()), "feature", base, false, true).ok()).isTrue();
    assertThat(rev(repo, "feature")).isEqualTo(base);
  }

  @Test
  void createAnnotatedTag() throws Exception {
    TempRepo repo = repo();
    assertThat(ops.createTag(new GitRepository(repo.root()), "v1.0", rev(repo, "HEAD"), "Release 1.0").ok()).isTrue();
    assertThat(repo.git("cat-file", "-t", "v1.0").trim()).isEqualTo("tag");
  }

  @Test
  void undoLastCommitKeepsChangesAndReturnsMessage() throws Exception {
    TempRepo repo = repo();
    GitRepository r = new GitRepository(repo.root());
    OperationResult result = ops.undoLastCommit(r);
    assertThat(result.ok()).isTrue();
    assertThat(result.message()).isEqualTo("m1");
    assertThat(repo.git("log", "-1", "--format=%s").trim()).isEqualTo("base");
    assertThat(status.status(r)).containsExactly(new FileChange("m.c", null, ChangeKind.ADDED));
  }

  @Test
  void failureIsReportedNotThrown() throws Exception {
    TempRepo repo = repo();
    OperationResult result = ops.createBranch(new GitRepository(repo.root()), "main", rev(repo, "HEAD"), false);
    assertThat(result.ok()).isFalse();
    assertThat(result.conflicts()).isFalse();
    assertThat(result.message()).contains("already exists");
  }
}
