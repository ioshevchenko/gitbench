package gitbench.git;

import gitbench.testutil.TempRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RepositoryLocatorTest {
  @TempDir
  Path tmp;

  private final RepositoryLocator locator = new RepositoryLocator(new GitRunner(() -> "git", GitCommandListener.NONE));

  @Test
  void findsRootFromNestedDir() throws Exception {
    TempRepo repo = TempRepo.init();
    repo.write("Core/Src/main.c", "int main(void) { return 0; }\n");
    assertThat(locator.find(repo.root().resolve("Core/Src")))
      .map(r -> r.root())
      .contains(repo.root());
  }

  @Test
  void emptyOutsideRepo() throws Exception {
    assertThat(locator.find(tmp)).isEmpty();
  }
}
