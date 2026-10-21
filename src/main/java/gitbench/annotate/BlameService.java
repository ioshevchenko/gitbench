package gitbench.annotate;

import gitbench.git.GitException;
import gitbench.git.GitRepository;
import gitbench.git.GitResult;
import gitbench.git.GitRunner;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Runs git blame for the text of an editor, so unsaved lines show as not committed. */
public final class BlameService {
  private final GitRunner runner;

  public BlameService(GitRunner runner) {
    this.runner = runner;
  }

  /** Blames {@code contents}, the editor text of {@code path}; the text goes to git in the charset of the file. */
  public Blame blame(GitRepository repository, String path, String contents, Charset charset) throws GitException {
    GitResult result = runner.run(repository.root(), List.of("blame", "--porcelain", "--contents", "-", "--", path),
                                  contents.getBytes(charset));
    if (!result.ok()) {
      throw GitException.fromResult("git blame", result);
    }
    // Git writes the names and the summaries in UTF-8; the file lines after the tabs are not read.
    return BlameParser.parse(new String(result.stdout(), StandardCharsets.UTF_8));
  }
}
