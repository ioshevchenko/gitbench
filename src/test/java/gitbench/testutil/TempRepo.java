package gitbench.testutil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** A throwaway git repository in a temporary folder, driven by the real git CLI. */
public final class TempRepo {
  private final Path root;

  private TempRepo(Path root) {
    this.root = root;
  }

  public static TempRepo init() {
    try {
      TempRepo repo = new TempRepo(Files.createTempDirectory("gitbench-repo").toRealPath());
      repo.git("init", "-q", "-b", "main");
      repo.configure();
      return repo;
    }
    catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Creates a bare repository that serves as a remote. */
  public static TempRepo initBare() {
    try {
      TempRepo repo = new TempRepo(Files.createTempDirectory("gitbench-remote").toRealPath());
      repo.git("init", "-q", "--bare", "-b", "main");
      return repo;
    }
    catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Clones {@code remote} into a new temporary folder. */
  public static TempRepo cloneOf(TempRepo remote) {
    try {
      Path dir = Files.createTempDirectory("gitbench-clone").toRealPath();
      runGit(dir, "clone", "-q", remote.root().toString(), ".");
      TempRepo repo = new TempRepo(dir);
      repo.configure();
      return repo;
    }
    catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private void configure() {
    git("config", "user.name", "Test User");
    git("config", "user.email", "test@example.com");
    git("config", "commit.gpgsign", "false");
    git("config", "core.autocrlf", "false");
  }

  public Path root() {
    return root;
  }

  public void write(String rel, String text) {
    try {
      Path file = root.resolve(rel);
      Files.createDirectories(file.getParent());
      Files.writeString(file, text, StandardCharsets.UTF_8);
    }
    catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public void commitAll(String msg) {
    git("add", "-A");
    git("commit", "-q", "-m", msg);
  }

  /** Runs git in the repository root and returns its stdout. Fails the test on a non-zero exit code. */
  public String git(String... args) {
    return runGit(root, args);
  }

  /** Runs git with {@code input} on stdin, for example {@code fast-import}. */
  public String gitWithInput(byte[] input, String... args) {
    return runGit(root, input, args);
  }

  public static String runGit(Path dir, String... args) {
    return runGit(dir, null, args);
  }

  private static String runGit(Path dir, byte[] input, String... args) {
    List<String> command = new ArrayList<>();
    command.add("git");
    command.add("-c");
    command.add("core.quotepath=false");
    command.addAll(List.of(args));
    try {
      Process process = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
      try (var stdin = process.getOutputStream()) {
        if (input != null) {
          stdin.write(input);
        }
      }
      String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      int code = process.waitFor();
      if (code != 0) {
        throw new IllegalStateException("git " + String.join(" ", args) + " failed: " + out);
      }
      return out;
    }
    catch (IOException | InterruptedException e) {
      throw new IllegalStateException(e);
    }
  }
}
