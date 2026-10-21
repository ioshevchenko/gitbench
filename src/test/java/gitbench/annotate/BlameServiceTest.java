package gitbench.annotate;

import gitbench.git.GitCommandListener;
import gitbench.git.GitRepository;
import gitbench.git.GitRunner;
import gitbench.testutil.TempRepo;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

class BlameServiceTest {
  private static final Charset CP1251 = Charset.forName("windows-1251");
  private final BlameService service = new BlameService(new GitRunner(() -> "git", GitCommandListener.NONE));

  private static TempRepo twoAuthors() {
    TempRepo repo = TempRepo.init();
    repo.write("f.c", "a\nb\n");
    repo.git("add", "f.c");
    repo.git("-c", "user.name=Ann", "-c", "user.email=ann@x", "commit", "-q", "-m", "first");
    repo.write("f.c", "a\nB\nc\n");
    repo.git("-c", "user.name=Bob", "-c", "user.email=bob@x", "commit", "-q", "-am", "second");
    return repo;
  }

  @Test
  void blamesCommittedLines() throws Exception {
    TempRepo repo = twoAuthors();

    Blame blame = service.blame(new GitRepository(repo.root()), "f.c", "a\nB\nc\n", StandardCharsets.UTF_8);

    assertThat(blame.lineHashes()).hasSize(3);
    assertThat(blame.commitAt(0).orElseThrow().author()).isEqualTo("Ann");
    assertThat(blame.commitAt(1).orElseThrow().author()).isEqualTo("Bob");
    assertThat(blame.commitAt(2).orElseThrow().summary()).isEqualTo("second");
  }

  @Test
  void blameWithDirtyContentsMarksNewLinesUncommitted() throws Exception {
    TempRepo repo = twoAuthors();

    Blame blame = service.blame(new GitRepository(repo.root()), "f.c", "a\ntyped\nB\nc\n", StandardCharsets.UTF_8);

    assertThat(blame.lineHashes()).hasSize(4);
    assertThat(blame.commitAt(1)).isEmpty();
    assertThat(blame.commitAt(2).orElseThrow().author()).isEqualTo("Bob");
  }

  @Test
  void blameEncodesContentsInFileCharset() throws Exception {
    TempRepo repo = TempRepo.init();
    Files.write(repo.root().resolve("ru.c"), "// Привет\nint a;\n".getBytes(CP1251));
    repo.commitAll("init");

    Blame blame = service.blame(new GitRepository(repo.root()), "ru.c", "// Привет\nint a;\n", CP1251);

    assertThat(blame.commitAt(0)).isPresent();
    assertThat(blame.commitAt(1)).isPresent();
  }
}
