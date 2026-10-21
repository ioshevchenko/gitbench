package gitbench.log;

import gitbench.git.ChangeKind;
import gitbench.git.FileChange;
import gitbench.git.GitCommandListener;
import gitbench.git.GitRepository;
import gitbench.git.GitRunner;
import gitbench.testutil.TempRepo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommitFilesLoaderTest {
  private final CommitFilesLoader loader = new CommitFilesLoader(new GitRunner(() -> "git", GitCommandListener.NONE));

  @Test
  void rootCommitListsAddedFiles() throws Exception {
    TempRepo repo = TempRepo.init();
    repo.write("Core/main.c", "m\n");
    repo.commitAll("init");
    assertThat(loader.load(new GitRepository(repo.root()), repo.git("rev-parse", "HEAD").trim()))
      .containsExactly(new FileChange("Core/main.c", null, ChangeKind.ADDED));
  }

  @Test
  void modifiedDeletedAndRenamed() throws Exception {
    TempRepo repo = TempRepo.init();
    repo.write("a.c", "a\n");
    repo.write("b.c", "b\n");
    repo.write("old.c", "same content for rename detection\n");
    repo.commitAll("init");
    repo.write("a.c", "a2\n");
    repo.git("rm", "-q", "b.c");
    repo.git("mv", "old.c", "new.c");
    repo.commitAll("change");
    assertThat(loader.load(new GitRepository(repo.root()), repo.git("rev-parse", "HEAD").trim()))
      .containsExactlyInAnyOrder(
        new FileChange("a.c", null, ChangeKind.MODIFIED),
        new FileChange("b.c", null, ChangeKind.DELETED),
        new FileChange("new.c", "old.c", ChangeKind.RENAMED));
  }

  @Test
  void mergeShowsChangesAgainstFirstParent() throws Exception {
    TempRepo repo = TempRepo.init();
    repo.write("a.c", "a\n");
    repo.commitAll("base");
    repo.git("switch", "-q", "-c", "feature");
    repo.write("f.c", "f\n");
    repo.commitAll("feature");
    repo.git("switch", "-q", "main");
    repo.write("m.c", "m\n");
    repo.commitAll("main");
    repo.git("merge", "-q", "--no-edit", "feature");
    assertThat(loader.load(new GitRepository(repo.root()), repo.git("rev-parse", "HEAD").trim()))
      .containsExactly(new FileChange("f.c", null, ChangeKind.ADDED));
  }
}
