package gitbench.git;

import gitbench.testutil.TempRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A CubeMX project with middleware has hundreds of long paths. Windows limits a command line to 32 767 chars. */
class LongPathsTest {
  private static final int FILES = 700;
  private static final String DIR = "Middlewares/Third_Party/FreeRTOS/Source/portable/GCC/ARM_CM4F/";

  private final GitRunner runner = new GitRunner(() -> "git", GitCommandListener.NONE);
  private final StatusService status = new StatusService(runner);

  @TempDir
  Path tmp;

  private static TempRepo repoWithManyFiles() {
    TempRepo repo = TempRepo.init();
    for (int i = 0; i < FILES; i++) {
      repo.write(DIR + "port_file_number_" + i + "_with_a_rather_long_name.c", "x\n");
    }
    return repo;
  }

  @Test
  void firstCommitOfManyLongPaths() throws Exception {
    TempRepo repo = repoWithManyFiles();
    GitRepository r = new GitRepository(repo.root());
    List<FileChange> all = status.status(r);
    assertThat(all.stream().mapToInt(c -> c.path().length() + 1).sum()).isGreaterThan(40_000);
    new CommitService(runner).commit(r, "initial", all, new CommitOptions(false, false, null));
    assertThat(status.status(r)).isEmpty();
  }

  @Test
  void rollbackOfManyLongPaths() throws Exception {
    TempRepo repo = repoWithManyFiles();
    repo.commitAll("initial");
    for (int i = 0; i < FILES; i++) {
      repo.write(DIR + "port_file_number_" + i + "_with_a_rather_long_name.c", "changed\n");
    }
    GitRepository r = new GitRepository(repo.root());
    new RollbackService(runner).rollback(r, status.status(r), false);
    assertThat(status.status(r)).isEmpty();
  }

  @Test
  void tooLongCommandLineIsNotReportedAsMissingGit() {
    List<String> args = new ArrayList<>(List.of("status", "--"));
    for (int i = 0; i < 2000; i++) {
      args.add("some/rather/long/path/number/" + i + ".c");
    }
    assertThatThrownBy(() -> runner.run(tmp, args, null))
      .isInstanceOfSatisfying(GitException.class, e -> assertThat(e.userMessage())
        .doesNotContain("not found")
        .contains("too long"));
  }

  @Test
  void keepsTmpDirClean() throws Exception {
    assertThat(Files.list(tmp).count()).isZero();
  }
}
