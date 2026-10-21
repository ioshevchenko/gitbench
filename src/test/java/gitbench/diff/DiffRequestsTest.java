package gitbench.diff;

import gitbench.git.ChangeKind;
import gitbench.git.FileChange;
import gitbench.git.GitRepository;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DiffRequestsTest {
  private final GitRepository repo = new GitRepository(Path.of("/work/repo").toAbsolutePath());

  @Test
  void modifiedFileComparesHeadWithWorkingFile() {
    DiffRequest request = DiffRequests.forLocalChange(repo, new FileChange("Core/main.c", null, ChangeKind.MODIFIED));

    assertThat(request.left()).isEqualTo(new DiffSide.Revision("HEAD", "Core/main.c"));
    assertThat(request.right()).isEqualTo(new DiffSide.WorkingFile(repo.root().resolve("Core/main.c")));
    assertThat(request.title()).isEqualTo("main.c");
  }

  @Test
  void addedFileHasEmptyLeft() {
    DiffRequest added = DiffRequests.forLocalChange(repo, new FileChange("new.c", null, ChangeKind.ADDED));
    DiffRequest unversioned = DiffRequests.forLocalChange(repo, new FileChange("tmp.c", null, ChangeKind.UNVERSIONED));

    assertThat(added.left()).isInstanceOf(DiffSide.Empty.class);
    assertThat(unversioned.left()).isInstanceOf(DiffSide.Empty.class);
    assertThat(added.right()).isInstanceOf(DiffSide.WorkingFile.class);
  }

  @Test
  void deletedFileHasEmptyRight() {
    DiffRequest request = DiffRequests.forLocalChange(repo, new FileChange("gone.c", null, ChangeKind.DELETED));

    assertThat(request.left()).isEqualTo(new DiffSide.Revision("HEAD", "gone.c"));
    assertThat(request.right()).isInstanceOf(DiffSide.Empty.class);
  }

  @Test
  void renamedFileUsesOldPathOnLeft() {
    DiffRequest request = DiffRequests.forLocalChange(repo, new FileChange("new.c", "old.c", ChangeKind.RENAMED));

    assertThat(request.left()).isEqualTo(new DiffSide.Revision("HEAD", "old.c"));
    assertThat(request.right()).isEqualTo(new DiffSide.WorkingFile(repo.root().resolve("new.c")));
  }

  @Test
  void commitFileComparesFirstParentWithCommit() {
    DiffRequest request = DiffRequests.forCommitFile(repo, "c0ffee", List.of("p1", "p2"), new FileChange("new.c", "old.c", ChangeKind.RENAMED));

    assertThat(request.left()).isEqualTo(new DiffSide.Revision("p1", "old.c"));
    assertThat(request.right()).isEqualTo(new DiffSide.Revision("c0ffee", "new.c"));
  }

  @Test
  void rootCommitHasEmptyLeft() {
    DiffRequest request = DiffRequests.forCommitFile(repo, "c0ffee", List.of(), new FileChange("a.c", null, ChangeKind.ADDED));

    assertThat(request.left()).isInstanceOf(DiffSide.Empty.class);
  }

  @Test
  void deletedInCommitHasEmptyRight() {
    DiffRequest request = DiffRequests.forCommitFile(repo, "c0ffee", List.of("p1"), new FileChange("a.c", null, ChangeKind.DELETED));

    assertThat(request.left()).isEqualTo(new DiffSide.Revision("p1", "a.c"));
    assertThat(request.right()).isInstanceOf(DiffSide.Empty.class);
  }
}
