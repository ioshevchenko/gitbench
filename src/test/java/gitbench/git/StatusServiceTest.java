package gitbench.git;

import gitbench.testutil.TempRepo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StatusServiceTest {
  @Test
  void reportsRealRepoState() throws Exception {
    TempRepo repo = TempRepo.init();
    repo.write("a.c", "a\n");
    repo.write("b.c", "b\n");
    repo.commitAll("init");
    repo.write("a.c", "a2\n");
    java.nio.file.Files.delete(repo.root().resolve("b.c"));
    repo.write("c.c", "c\n");

    StatusService service = new StatusService(new GitRunner(() -> "git", GitCommandListener.NONE));
    assertThat(service.status(new GitRepository(repo.root()))).containsExactlyInAnyOrder(
      new FileChange("a.c", null, ChangeKind.MODIFIED),
      new FileChange("b.c", null, ChangeKind.DELETED),
      new FileChange("c.c", null, ChangeKind.UNVERSIONED));
  }
}
