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
  private static final String BOM = "﻿";
  private final GitRunner runner;

  /**
   * Whether the file on disk starts with a UTF-8 BOM. The editor keeps the BOM apart from the document text, so the
   * text gets it back before git compares it with a committed file that has one.
   */
  private static boolean hasBom(java.nio.file.Path file) {
    try (java.io.InputStream in = java.nio.file.Files.newInputStream(file)) {
      byte[] start = in.readNBytes(3);
      return start.length == 3 && start[0] == (byte)0xEF && start[1] == (byte)0xBB && start[2] == (byte)0xBF;
    }
    catch (java.io.IOException e) {
      return false;
    }
  }

  public BlameService(GitRunner runner) {
    this.runner = runner;
  }

  /** Blames {@code contents}, the editor text of {@code path}; the text goes to git in the charset of the file. */
  public Blame blame(GitRepository repository, String path, String contents, Charset charset) throws GitException {
    String text = hasBom(repository.root().resolve(path)) && !contents.startsWith(BOM) ? BOM + contents : contents;
    GitResult result = runner.run(repository.root(), List.of("blame", "--porcelain", "--contents", "-", "--", path),
                                  text.getBytes(charset));
    if (!result.ok()) {
      throw GitException.fromResult("git blame", result);
    }
    // Git writes the names and the summaries in UTF-8; the file lines after the tabs are not read.
    return BlameParser.parse(new String(result.stdout(), StandardCharsets.UTF_8));
  }
}
