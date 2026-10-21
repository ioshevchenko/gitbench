package gitbench.diff;

import gitbench.git.GitCommandListener;
import gitbench.git.GitRepository;
import gitbench.git.GitRunner;
import gitbench.testutil.TempRepo;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

class DiffContentLoaderTest {
  private static final Charset CP1251 = Charset.forName("windows-1251");
  private final GitRunner runner = new GitRunner(() -> "git", GitCommandListener.NONE);

  @Test
  void loadsRevisionContent() throws Exception {
    TempRepo repo = TempRepo.init();
    repo.write("Core/main.c", "int main;\n");
    repo.commitAll("init");
    repo.write("Core/main.c", "changed\n");

    LoadedContent content = DiffContentLoader.load(runner, new GitRepository(repo.root()), new DiffSide.Revision("HEAD", "Core/main.c"),
                                                   StandardCharsets.UTF_8);

    assertThat(content.text()).isEqualTo("int main;\n");
    assertThat(content.binary()).isFalse();
    assertThat(content.lineSeparator()).isEqualTo("\n");
    assertThat(content.title()).hasSize(8).isEqualTo(repo.git("rev-parse", "HEAD").trim().substring(0, 8));
  }

  @Test
  void loadsWorkingFile() throws Exception {
    TempRepo repo = TempRepo.init();
    repo.write("a.c", "x\r\ny\r\n");

    LoadedContent content = DiffContentLoader.load(runner, new GitRepository(repo.root()), new DiffSide.WorkingFile(repo.root().resolve("a.c")),
                                                   StandardCharsets.UTF_8);

    assertThat(content.text()).isEqualTo("x\r\ny\r\n");
    assertThat(content.lineSeparator()).isEqualTo("\r\n");
    assertThat(content.title()).isEqualTo("Your version");
  }

  @Test
  void decodesRevisionWithWorkspaceCharset() throws Exception {
    TempRepo repo = TempRepo.init();
    Files.write(repo.root().resolve("ru.c"), "// привет\n".getBytes(CP1251));
    repo.commitAll("init");

    LoadedContent content = DiffContentLoader.load(runner, new GitRepository(repo.root()), new DiffSide.Revision("HEAD", "ru.c"), CP1251);

    assertThat(content.text()).isEqualTo("// привет\n");
    assertThat(content.charset()).isEqualTo(CP1251);
  }

  @Test
  void utf8BomIsNotAChange() throws Exception {
    TempRepo repo = TempRepo.init();
    byte[] bom = {(byte)0xEF, (byte)0xBB, (byte)0xBF};
    byte[] body = "a\nb\n".getBytes(StandardCharsets.UTF_8);
    byte[] withBom = new byte[bom.length + body.length];
    System.arraycopy(bom, 0, withBom, 0, bom.length);
    System.arraycopy(body, 0, withBom, bom.length, body.length);
    Files.write(repo.root().resolve("bom.c"), withBom);
    repo.commitAll("init");
    Files.write(repo.root().resolve("bom.c"), body);
    GitRepository repository = new GitRepository(repo.root());

    LoadedContent left = DiffContentLoader.load(runner, repository, new DiffSide.Revision("HEAD", "bom.c"), CP1251);
    LoadedContent right = DiffContentLoader.load(runner, repository, new DiffSide.WorkingFile(repo.root().resolve("bom.c")), CP1251);

    assertThat(left.charset()).isEqualTo(StandardCharsets.UTF_8);
    assertThat(DiffModel.compute(DiffText.of(left.text()), DiffText.of(right.text()), IgnorePolicy.DEFAULT, HighlightPolicy.BY_WORD)).isEmpty();
  }

  @Test
  void binaryContentIsDetected() throws Exception {
    TempRepo repo = TempRepo.init();
    Files.write(repo.root().resolve("fw.bin"), new byte[]{0x7F, 'E', 'L', 'F', 0, 1, 2});
    repo.commitAll("init");

    LoadedContent content = DiffContentLoader.load(runner, new GitRepository(repo.root()), new DiffSide.Revision("HEAD", "fw.bin"),
                                                   StandardCharsets.UTF_8);

    assertThat(content.binary()).isTrue();
    assertThat(content.text()).isEmpty();
  }

  @Test
  void emptySideHasNoText() throws Exception {
    LoadedContent content = DiffContentLoader.load(runner, new GitRepository(TempRepo.init().root()), new DiffSide.Empty("File added"),
                                                   StandardCharsets.UTF_8);

    assertThat(content.text()).isEmpty();
    assertThat(content.title()).isEqualTo("File added");
  }

  @Test
  void pathWithSpacesAndCyrillicLoads() throws Exception {
    TempRepo repo = TempRepo.init();
    repo.write("Мои файлы/main file.c", "x\n");
    repo.commitAll("init");

    LoadedContent content = DiffContentLoader.load(runner, new GitRepository(repo.root()),
                                                   new DiffSide.Revision("HEAD", "Мои файлы/main file.c"), StandardCharsets.UTF_8);

    assertThat(content.text()).isEqualTo("x\n");
  }
}
