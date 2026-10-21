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

  @Test
  void autocrlfWorkingFileGivesCrlfSeparator() throws Exception {
    TempRepo repo = TempRepo.init();
    repo.git("config", "core.autocrlf", "true");
    repo.write("a.c", "base\r\n");
    repo.commitAll("base");
    repo.git("checkout", "-q", "-b", "feature");
    repo.write("a.c", "theirs\r\n");
    repo.commitAll("theirs");
    repo.git("checkout", "-q", "main");
    repo.write("a.c", "ours\r\n");
    repo.commitAll("ours");
    repo.gitMayFail("merge", "-q", "feature");

    // The stage blobs hold LF; the working file holds CRLF, and the result must keep it.
    assertThat(load(repo).lineSeparator()).isEqualTo("\r\n");
  }

  @Test
  void bomFileKeepsBomAndUtf8() throws Exception {
    TempRepo repo = TempRepo.init();
    byte[] bom = {(byte)0xEF, (byte)0xBB, (byte)0xBF};
    java.util.function.Function<String, byte[]> withBom = text -> {
      byte[] body = text.getBytes(StandardCharsets.UTF_8);
      byte[] all = new byte[bom.length + body.length];
      System.arraycopy(bom, 0, all, 0, bom.length);
      System.arraycopy(body, 0, all, bom.length, body.length);
      return all;
    };
    Files.write(repo.root().resolve("a.c"), withBom.apply("база" + "\n"));
    repo.commitAll("base");
    repo.git("checkout", "-q", "-b", "feature");
    Files.write(repo.root().resolve("a.c"), withBom.apply("их" + "\n"));
    repo.commitAll("theirs");
    repo.git("checkout", "-q", "main");
    Files.write(repo.root().resolve("a.c"), withBom.apply("наши" + "\n"));
    repo.commitAll("ours");
    repo.gitMayFail("merge", "-q", "feature");
    GitRepository repository = new GitRepository(repo.root());

    MergeContents contents = MergeContentLoader.load(runner, repository, service.list(repository).get(0), service.context(repository),
                                                     java.nio.charset.Charset.forName("windows-1252"));

    assertThat(contents.bom()).isTrue();
    assertThat(contents.charset()).isEqualTo(StandardCharsets.UTF_8);
    assertThat(contents.yours().text()).isEqualTo("наши\n");
  }
}
