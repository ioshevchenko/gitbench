package gitbench.merge;

import gitbench.git.GitCommandListener;
import gitbench.git.GitRepository;
import gitbench.git.GitRunner;
import gitbench.testutil.TempRepo;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

class MergeContentLoaderTest {
  private final GitRunner runner = new GitRunner(() -> "git", GitCommandListener.NONE);
  private final ConflictService service = new ConflictService(runner);

  private MergeContents load(TempRepo repo) throws Exception {
    GitRepository repository = new GitRepository(repo.root());
    return MergeContentLoader.load(runner, repository, service.list(repository).get(0), service.context(repository),
                                   StandardCharsets.UTF_8);
  }

  @Test
  void loadsThreeStages() throws Exception {
    MergeContents contents = load(ConflictServiceTest.mergeConflict());

    assertThat(contents.yours().text()).isEqualTo("ours\n");
    assertThat(contents.base().text()).isEqualTo("base\n");
    assertThat(contents.theirs().text()).isEqualTo("theirs\n");
    assertThat(contents.binary()).isFalse();
  }

  @Test
  void bothAddedHasEmptyBase() throws Exception {
    TempRepo repo = TempRepo.init();
    repo.write("x.txt", "x\n");
    repo.commitAll("init");
    repo.git("checkout", "-q", "-b", "feature");
    repo.write("a.c", "theirs\n");
    repo.commitAll("theirs");
    repo.git("checkout", "-q", "main");
    repo.write("a.c", "ours\n");
    repo.commitAll("ours");
    repo.gitMayFail("merge", "-q", "feature");

    MergeContents contents = load(repo);

    assertThat(contents.base().text()).isEmpty();
    assertThat(contents.yours().text()).isEqualTo("ours\n");
  }

  @Test
  void rebaseSwapsSides() throws Exception {
    TempRepo repo = ConflictServiceTest.divergedRepo();
    repo.git("checkout", "-q", "feature");
    repo.gitMayFail("rebase", "main");

    MergeContents contents = load(repo);

    // Yours is the commit being rebased (feature: "theirs"), Theirs is main ("ours").
    assertThat(contents.yours().text()).isEqualTo("theirs\n");
    assertThat(contents.theirs().text()).isEqualTo("ours\n");
  }

  @Test
  void binaryStageIsDetected() throws Exception {
    TempRepo repo = TempRepo.init();
    Files.write(repo.root().resolve("fw.bin"), new byte[]{1, 0, 2});
    repo.commitAll("base");
    repo.git("checkout", "-q", "-b", "feature");
    Files.write(repo.root().resolve("fw.bin"), new byte[]{1, 0, 3});
    repo.commitAll("theirs");
    repo.git("checkout", "-q", "main");
    Files.write(repo.root().resolve("fw.bin"), new byte[]{1, 0, 4});
    repo.commitAll("ours");
    repo.gitMayFail("merge", "-q", "feature");

    assertThat(load(repo).binary()).isTrue();
  }

  @Test
  void crlfWorkingFileGivesCrlfSeparator() throws Exception {
    TempRepo repo = TempRepo.init();
    repo.write("a.c", "base\r\n");
    repo.commitAll("base");
    repo.git("checkout", "-q", "-b", "feature");
    repo.write("a.c", "theirs\r\n");
    repo.commitAll("theirs");
    repo.git("checkout", "-q", "main");
    repo.write("a.c", "ours\r\n");
    repo.commitAll("ours");
    repo.gitMayFail("merge", "-q", "feature");

    assertThat(load(repo).lineSeparator()).isEqualTo("\r\n");
  }
}
