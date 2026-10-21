package gitbench.git;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitRunnerTest {
  @TempDir
  Path tmp;

  @Test
  void runsGitVersion() throws Exception {
    GitResult result = new GitRunner(() -> "git", GitCommandListener.NONE).run(tmp, List.of("--version"), null);
    assertThat(result.ok()).isTrue();
    assertThat(result.stdoutText()).startsWith("git version");
  }

  @Test
  void missingGitExecutableGivesReadableError() {
    GitRunner runner = new GitRunner(() -> "C:/no/such/git.exe", GitCommandListener.NONE);
    assertThatThrownBy(() -> runner.run(tmp, List.of("--version"), null))
      .isInstanceOfSatisfying(GitException.class, e -> assertThat(e.userMessage())
        .contains("Git executable not found")
        .contains("C:/no/such/git.exe"));
  }

  @Test
  void nonZeroExitIsResultNotException() throws Exception {
    GitResult result = new GitRunner(() -> "git", GitCommandListener.NONE).run(tmp, List.of("rev-parse", "HEAD"), null);
    assertThat(result.exitCode()).isNotZero();
    assertThat(result.stderr()).isNotBlank();
  }

  @Test
  void notifiesListener() throws Exception {
    List<List<String>> seen = new ArrayList<>();
    GitRunner runner = new GitRunner(() -> "git", (dir, args, result) -> seen.add(args));
    runner.run(tmp, List.of("--version"), null);
    assertThat(seen).containsExactly(List.of("--version"));
  }

  @Test
  void passesStdin() throws Exception {
    GitResult result = new GitRunner(() -> "git", GitCommandListener.NONE)
      .run(tmp, List.of("hash-object", "--stdin"), "hello\n".getBytes());
    assertThat(result.stdoutText().trim()).isEqualTo("ce013625030ba8dba906f756967f9e9ca394464a");
  }
}
