package gitbench.merge;

import gitbench.diff.DiffContentLoader;
import gitbench.diff.LoadedContent;
import gitbench.git.GitException;
import gitbench.git.GitRepository;
import gitbench.git.GitResult;
import gitbench.git.GitRunner;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Reads the git stages of a conflicted file: 1 is the base, 2 is ours, 3 is theirs. */
public final class MergeContentLoader {
  private MergeContentLoader() {
  }

  /**
   * Loads the three sides. A missing stage, for example the base of a file that both sides added, is an empty text.
   * The line separator and the BOM come from the working file: git writes it with the conflict markers in the file's
   * own line separator, while the stage blobs may hold LF because of {@code core.autocrlf}.
   */
  public static MergeContents load(GitRunner runner, GitRepository repo, ConflictFile file, MergeContext context, Charset charset)
    throws GitException {
    byte[] oursBytes = stageBytes(runner, repo, file.path(), 2);
    byte[] baseBytes = stageBytes(runner, repo, file.path(), 1);
    byte[] theirsBytes = stageBytes(runner, repo, file.path(), 3);
    byte[] working = workingFile(repo.root().resolve(file.path()));
    LoadedContent ours = decode(oursBytes, charset, context.reversed() ? context.theirsTitle() : context.yoursTitle());
    LoadedContent base = decode(baseBytes, charset, "Base version");
    LoadedContent theirs = decode(theirsBytes, charset, context.reversed() ? context.yoursTitle() : context.theirsTitle());
    LoadedContent yours = context.reversed() ? theirs : ours;
    LoadedContent theirsSide = context.reversed() ? ours : theirs;
    boolean bom = hasBom(working) || hasBom(context.reversed() ? theirsBytes : oursBytes);
    Charset used = bom ? StandardCharsets.UTF_8 : yours.charset();
    String separator = separatorOf(working);
    if (separator == null) {
      separator = !yours.text().isEmpty() ? yours.lineSeparator() : !theirsSide.text().isEmpty() ? theirsSide.lineSeparator() : base.lineSeparator();
    }
    return new MergeContents(yours, base, theirsSide, separator, used, bom);
  }

  private static byte[] stageBytes(GitRunner runner, GitRepository repo, String path, int stage) throws GitException {
    GitResult blob = runner.run(repo.root(), List.of("cat-file", "blob", ":" + stage + ":" + path), null);
    return blob.ok() ? blob.stdout() : null;
  }

  private static LoadedContent decode(byte[] bytes, Charset charset, String title) {
    return bytes == null ? new LoadedContent("", false, charset, "\n", title) : DiffContentLoader.decode(bytes, charset, title);
  }

  private static byte[] workingFile(Path file) throws GitException {
    try {
      return Files.exists(file) ? Files.readAllBytes(file) : null;
    }
    catch (IOException e) {
      throw new GitException("Cannot read " + file + ": " + e.getMessage(), e);
    }
  }

  private static boolean hasBom(byte[] bytes) {
    return bytes != null && bytes.length >= 3 && bytes[0] == (byte)0xEF && bytes[1] == (byte)0xBB && bytes[2] == (byte)0xBF;
  }

  /** The first line separator of the file, or null when it has none. */
  private static String separatorOf(byte[] bytes) {
    if (bytes == null) {
      return null;
    }
    for (int i = 0; i < bytes.length; i++) {
      if (bytes[i] == '\n') {
        return i > 0 && bytes[i - 1] == '\r' ? "\r\n" : "\n";
      }
      if (bytes[i] == '\r' && (i + 1 == bytes.length || bytes[i + 1] != '\n')) {
        return "\r";
      }
    }
    return null;
  }
}
