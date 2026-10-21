package gitbench.git;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers which folder belongs to which repository. A failed lookup is not remembered, so a fixed git path
 * in Preferences works at once. Safe for use from background jobs.
 */
public final class RepositoryCache {
  private final RepositoryLocator locator;
  private final Map<Path, Optional<GitRepository>> found = new ConcurrentHashMap<>();

  public RepositoryCache(RepositoryLocator locator) {
    this.locator = locator;
  }

  public Optional<GitRepository> find(Path dir) throws GitException {
    Optional<GitRepository> cached = found.get(dir);
    if (cached != null) {
      return cached;
    }
    Optional<GitRepository> repository = locator.find(dir);
    found.put(dir, repository);
    return repository;
  }

  public void clear() {
    found.clear();
  }
}
