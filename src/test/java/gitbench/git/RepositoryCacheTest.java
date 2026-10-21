package gitbench.git;

import gitbench.testutil.TempRepo;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepositoryCacheTest {
  private final AtomicReference<String> gitPath = new AtomicReference<>("git");
  private final AtomicInteger calls = new AtomicInteger();
  private final RepositoryCache cache =
    new RepositoryCache(new RepositoryLocator(new GitRunner(gitPath::get, (dir, args, result) -> calls.incrementAndGet())));

  @Test
  void lookupResultsAreCached() throws Exception {
    TempRepo repo = TempRepo.init();
    assertThat(cache.find(repo.root())).contains(new GitRepository(repo.root()));
    assertThat(cache.find(repo.root())).contains(new GitRepository(repo.root()));
    assertThat(calls.get()).isEqualTo(1);
  }

  @Test
  void failuresAreNotCached() throws Exception {
    TempRepo repo = TempRepo.init();
    gitPath.set("C:/no/such/git.exe");
    assertThatThrownBy(() -> cache.find(repo.root()))
      .isInstanceOfSatisfying(GitException.class, e -> assertThat(e.userMessage()).contains("Git executable not found"));
    gitPath.set("git");
    assertThat(cache.find(repo.root())).contains(new GitRepository(repo.root()));
  }

  @Test
  void clearDropsCachedResults() throws Exception {
    TempRepo repo = TempRepo.init();
    cache.find(repo.root());
    cache.clear();
    cache.find(repo.root());
    assertThat(calls.get()).isEqualTo(2);
  }
}
