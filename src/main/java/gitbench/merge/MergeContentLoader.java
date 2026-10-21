package gitbench.merge;

import gitbench.diff.DiffContentLoader;
import gitbench.diff.LoadedContent;
import gitbench.git.GitException;
import gitbench.git.GitRepository;
import gitbench.git.GitResult;
import gitbench.git.GitRunner;

import java.nio.charset.Charset;
import java.util.List;

/** Reads the git stages of a conflicted file: 1 is the base, 2 is ours, 3 is theirs. */
public final class MergeContentLoader {
  private MergeContentLoader() {
  }

  /** A missing stage, for example the base of a file that both sides added, is an empty text. */
  public static MergeContents load(GitRunner runner, GitRepository repo, ConflictFile file, MergeContext context, Charset charset)
    throws GitException {
    LoadedContent ours = stage(runner, repo, file.path(), 2, charset, context.reversed() ? context.theirsTitle() : context.yoursTitle());
    LoadedContent base = stage(runner, repo, file.path(), 1, charset, "Base version");
    LoadedContent theirs = stage(runner, repo, file.path(), 3, charset, context.reversed() ? context.yoursTitle() : context.theirsTitle());
    LoadedContent yours = context.reversed() ? theirs : ours;
    LoadedContent theirsSide = context.reversed() ? ours : theirs;
    String separator = !yours.text().isEmpty() ? yours.lineSeparator() : !theirsSide.text().isEmpty() ? theirsSide.lineSeparator() : base.lineSeparator();
    return new MergeContents(yours, base, theirsSide, separator);
  }

  private static LoadedContent stage(GitRunner runner, GitRepository repo, String path, int stage, Charset charset, String title)
    throws GitException {
    GitResult blob = runner.run(repo.root(), List.of("cat-file", "blob", ":" + stage + ":" + path), null);
    if (!blob.ok()) {
      return new LoadedContent("", false, charset, "\n", title);
    }
    return DiffContentLoader.decode(blob.stdout(), charset, title);
  }
}
