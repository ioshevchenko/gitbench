package gitbench.status;

import gitbench.git.GitCommandListener;
import gitbench.git.GitRunner;
import gitbench.git.RepositoryLocator;
import gitbench.testutil.TempRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LineStatusBaseTest {
  private static final Charset CP1251 = Charset.forName("windows-1251");
  private final GitRunner runner = new GitRunner(() -> "git", GitCommandListener.NONE);
  private final RepositoryLocator locator = new RepositoryLocator(runner);

  private Optional<String> base(Path file, Charset charset) throws Exception {
    return head(file, charset).map(LineStatusBase.Head::text);
  }

  private Optional<LineStatusBase.Head> head(Path file, Charset charset) throws Exception {
    Optional<LineStatusBase.Key> key = LineStatusBase.find(locator, file);
    return key.isEmpty() ? Optional.empty() : LineStatusBase.load(runner, key.get(), charset);
  }

  @Test
  void loadsHeadContent() throws Exception {
    TempRepo repo = TempRepo.init();
    repo.write("Core/main.c", "int main;\n");
    repo.commitAll("init");
    repo.write("Core/main.c", "changed\n");

    Optional<LineStatusBase.Key> key = LineStatusBase.find(locator, repo.root().resolve("Core/main.c"));

    assertThat(key).isPresent();
    assertThat(key.get().path()).isEqualTo("Core/main.c");
    assertThat(LineStatusBase.load(runner, key.get(), StandardCharsets.UTF_8).map(LineStatusBase.Head::text)).contains("int main;\n");
  }

  @Test
  void untrackedFileHasNoBase() throws Exception {
    TempRepo repo = TempRepo.init();
    repo.write("a.c", "a\n");
    repo.commitAll("init");
    repo.write("new.c", "x\n");

    assertThat(base(repo.root().resolve("new.c"), StandardCharsets.UTF_8)).isEmpty();
  }

  @Test
  void addedFileHasEmptyBase() throws Exception {
    TempRepo repo = TempRepo.init();
    repo.write("a.c", "a\n");
    repo.commitAll("init");
    repo.write("new.c", "x\n");
    repo.git("add", "new.c");

    assertThat(head(repo.root().resolve("new.c"), StandardCharsets.UTF_8)).hasValueSatisfying(head -> {
      assertThat(head.text()).isEmpty();
      assertThat(head.inHead()).isFalse();
    });
  }

  @Test
  void emptyCommittedFileIsInHead() throws Exception {
    TempRepo repo = TempRepo.init();
    repo.write("empty.c", "");
    repo.commitAll("init");

    assertThat(head(repo.root().resolve("empty.c"), StandardCharsets.UTF_8)).hasValueSatisfying(head -> {
      assertThat(head.text()).isEmpty();
      assertThat(head.inHead()).isTrue();
    });
  }

  @Test
  void baseGoesThroughTheWorkingTreeEncoding() throws Exception {
    // Git keeps the file as UTF-8 and writes it to the working tree in windows-1251.
    TempRepo repo = TempRepo.init();
    repo.write(".gitattributes", "*.c working-tree-encoding=windows-1251\n");
    Files.write(repo.root().resolve("ru.c"), "// Привет\n".getBytes(CP1251));
    repo.commitAll("init");

    assertThat(base(repo.root().resolve("ru.c"), CP1251)).contains("// Привет\n");
  }

  @Test
  void fileOutsideRepositoryHasNoBase(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("plain.c");
    Files.writeString(file, "x\n");

    assertThat(base(file, StandardCharsets.UTF_8)).isEmpty();
  }

  @Test
  void baseUsesFileCharset() throws Exception {
    TempRepo repo = TempRepo.init();
    Files.write(repo.root().resolve("ru.c"), "// Привет\n".getBytes(CP1251));
    repo.commitAll("init");

    assertThat(base(repo.root().resolve("ru.c"), CP1251)).contains("// Привет\n");
  }
}
