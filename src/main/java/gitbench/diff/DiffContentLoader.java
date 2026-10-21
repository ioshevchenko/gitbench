package gitbench.diff;

import gitbench.git.GitException;
import gitbench.git.GitRepository;
import gitbench.git.GitResult;
import gitbench.git.GitRunner;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.util.List;

/** Reads a diff side from git or from the disk. */
public final class DiffContentLoader {
  /** Git and IntelliJ look for a zero byte in the first 8000 bytes to call a file binary. */
  private static final int BINARY_CHECK_LENGTH = 8000;
  private static final int SHORT_HASH_LENGTH = 8;

  private DiffContentLoader() {
  }

  /**
   * Loads the side. {@code charset} is the encoding of the file in the workspace; a UTF-8 BOM overrides it, as in the
   * Eclipse text editors.
   */
  public static LoadedContent load(GitRunner runner, GitRepository repository, DiffSide side, Charset charset) throws GitException {
    if (side instanceof DiffSide.Empty empty) {
      return new LoadedContent("", false, charset, "\n", empty.label());
    }
    if (side instanceof DiffSide.WorkingFile file) {
      try {
        return decode(Files.readAllBytes(file.absolutePath()), charset, "Your version");
      }
      catch (NoSuchFileException e) {
        return new LoadedContent("", false, charset, "\n", "Your version");
      }
      catch (IOException e) {
        throw new GitException("Cannot read " + file.absolutePath() + ": " + e.getMessage(), e);
      }
    }
    DiffSide.Revision revision = (DiffSide.Revision)side;
    GitResult blob = runner.run(repository.root(), List.of("cat-file", "blob", revision.revision() + ":" + revision.path()), null);
    if (!blob.ok()) {
      throw GitException.fromResult("git cat-file", blob);
    }
    return decode(blob.stdout(), charset, shortHash(runner, repository, revision.revision()));
  }

  private static String shortHash(GitRunner runner, GitRepository repository, String revision) throws GitException {
    GitResult result = runner.run(repository.root(), List.of("rev-parse", "--verify", "--quiet", revision + "^{commit}"), null);
    String hash = result.ok() ? result.stdoutText().trim() : revision;
    return hash.length() > SHORT_HASH_LENGTH ? hash.substring(0, SHORT_HASH_LENGTH) : hash;
  }

  /** Decodes file bytes as a diff side: binary check, UTF-8 BOM, charset and the first line separator. */
  public static LoadedContent decode(byte[] bytes, Charset charset, String title) {
    int checked = Math.min(bytes.length, BINARY_CHECK_LENGTH);
    for (int i = 0; i < checked; i++) {
      if (bytes[i] == 0) {
        return new LoadedContent("", true, charset, "\n", title);
      }
    }
    Charset used = charset;
    int start = 0;
    if (bytes.length >= 3 && bytes[0] == (byte)0xEF && bytes[1] == (byte)0xBB && bytes[2] == (byte)0xBF) {
      used = StandardCharsets.UTF_8;
      start = 3;
    }
    String text = new String(bytes, start, bytes.length - start, used);
    return new LoadedContent(text, false, used, lineSeparator(text), title);
  }

  private static String lineSeparator(String text) {
    int newline = text.indexOf('\n');
    if (newline > 0 && text.charAt(newline - 1) == '\r') {
      return "\r\n";
    }
    if (newline < 0 && text.indexOf('\r') >= 0) {
      return "\r";
    }
    return "\n";
  }
}
