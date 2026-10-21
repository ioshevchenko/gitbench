package gitbench.git;

import gitbench.testutil.TempRepo;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IgnoreServiceTest {
  private final IgnoreService ignore = new IgnoreService();

  @Test
  void addToGitignoreAppendsLine() throws Exception {
    TempRepo repo = TempRepo.init();
    repo.write(".gitignore", "*.o");
    GitRepository r = new GitRepository(repo.root());
    ignore.addToGitignore(r, List.of("build/out.bin"));
    ignore.addToGitignore(r, List.of("build/out.bin"));
    assertThat(Files.readAllLines(repo.root().resolve(".gitignore"))).containsExactly("*.o", "/build/out.bin");
  }

  @Test
  void createsGitignoreWhenMissing() throws Exception {
    TempRepo repo = TempRepo.init();
    ignore.addToGitignore(new GitRepository(repo.root()), List.of("a.bin"));
    assertThat(Files.readAllLines(repo.root().resolve(".gitignore"))).containsExactly("/a.bin");
  }
}
