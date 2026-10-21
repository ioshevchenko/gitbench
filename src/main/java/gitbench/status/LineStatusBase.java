package gitbench.status;

import gitbench.diff.DiffContentLoader;
import gitbench.diff.LoadedContent;
import gitbench.git.GitException;
import gitbench.git.GitRepository;
import gitbench.git.GitResult;
import gitbench.git.GitRunner;
import gitbench.git.RepositoryLocator;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** The HEAD text that the change markers of a file compare with. */
public final class LineStatusBase {
  /** A file of a repository; the path is relative to the repository root and uses '/'. */
  public record Key(GitRepository repository, String path) {
  }

  /** The HEAD text of a tracked file; {@code inHead} is false for a file that HEAD does not have yet, as an added one. */
  public record Head(String text, boolean inHead) {
  }

  private LineStatusBase() {
  }

  /** The repository and the relative path of {@code file}; empty outside a repository. */
  public static Optional<Key> find(RepositoryLocator locator, Path file) throws GitException {
    Path parent = file.toAbsolutePath().getParent();
    if (parent == null) {
      return Optional.empty();
    }
    Optional<GitRepository> repository = locator.find(parent);
    if (repository.isEmpty()) {
      return Optional.empty();
    }
    try {
      // The repository root is a real path, so the file must be one too: a short Windows name would not relativize.
      Path real = parent.toRealPath().resolve(file.getFileName());
      String path = repository.get().root().relativize(real).toString().replace('\\', '/');
      return Optional.of(new Key(repository.get(), path));
    }
    catch (IOException | IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  /**
   * The HEAD text in {@code charset}, as the working tree would get it: through the filters of the file, such as
   * {@code working-tree-encoding} and Git LFS. Empty for a file git does not track and for a binary file; an empty
   * text that is not in HEAD for a tracked file that HEAD does not have yet, as an added file.
   */
  public static Optional<Head> load(GitRunner runner, Key key, Charset charset) throws GitException {
    Path root = key.repository().root();
    GitResult tracked = runner.run(root, List.of("ls-files", "--error-unmatch", "--", key.path()), null);
    if (!tracked.ok()) {
      return Optional.empty();
    }
    GitResult blob = runner.run(root, List.of("cat-file", "--filters", "HEAD:" + key.path()), null);
    if (!blob.ok()) {
      return Optional.of(new Head("", false));
    }
    LoadedContent content = DiffContentLoader.decode(blob.stdout(), charset, "HEAD");
    return content.binary() ? Optional.empty() : Optional.of(new Head(content.text(), true));
  }
}
