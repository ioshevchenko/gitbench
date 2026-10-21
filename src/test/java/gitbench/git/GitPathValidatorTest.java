package gitbench.git;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GitPathValidatorTest {
  @TempDir
  Path tmp;

  @Test
  void bareCommandNameIsValid() {
    assertThat(GitPathValidator.error("git")).isNull();
    assertThat(GitPathValidator.error("git.exe")).isNull();
  }

  @Test
  void blankMeansDefault() {
    assertThat(GitPathValidator.error("")).isNull();
  }

  @Test
  void existingFileIsValid() throws Exception {
    Path git = Files.createFile(tmp.resolve("git.exe"));
    assertThat(GitPathValidator.error(git.toString())).isNull();
  }

  @Test
  void missingFileWithFolderIsInvalid() {
    assertThat(GitPathValidator.error(tmp.resolve("bin/git.exe").toString())).contains("does not exist");
  }

  @Test
  void folderIsInvalid() {
    assertThat(GitPathValidator.error(tmp.toString())).contains("does not exist");
  }
}
