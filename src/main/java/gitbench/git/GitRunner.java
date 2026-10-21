package gitbench.git;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Runs the git CLI. The path to the executable is read for every call, so a preference change applies at once. */
public final class GitRunner {
  /** Paths from the UI are file names, never glob patterns, so every pathspec is literal. */
  private static final List<String> FIXED_OPTIONS =
    List.of("--literal-pathspecs", "-c", "core.quotepath=false", "-c", "color.ui=false");

  private final Supplier<String> gitPath;
  private final GitCommandListener listener;

  public GitRunner(Supplier<String> gitPath, GitCommandListener listener) {
    this.gitPath = gitPath;
    this.listener = listener;
  }

  /** Runs git in {@code workDir}. A non-zero exit code is a result, not an exception. */
  public GitResult run(Path workDir, List<String> args, byte[] stdin) throws GitException {
    String executable = gitPath.get();
    List<String> command = new ArrayList<>();
    command.add(executable);
    command.addAll(FIXED_OPTIONS);
    command.addAll(args);
    ProcessBuilder builder = new ProcessBuilder(command).directory(workDir.toFile());
    builder.environment().put("LC_ALL", "C");
    builder.environment().put("GIT_TERMINAL_PROMPT", "0");
    Process process;
    try {
      process = builder.start();
    }
    catch (IOException e) {
      throw startFailure(executable, e);
    }
    try {
      CompletableFuture<byte[]> out = CompletableFuture.supplyAsync(() -> readAll(process.getInputStream()));
      CompletableFuture<byte[]> err = CompletableFuture.supplyAsync(() -> readAll(process.getErrorStream()));
      try (OutputStream in = process.getOutputStream()) {
        if (stdin != null) {
          in.write(stdin);
        }
      }
      int code = process.waitFor();
      GitResult result = new GitResult(code, out.join(), new String(err.join(), StandardCharsets.UTF_8));
      listener.onCommand(workDir, List.copyOf(args), result);
      return result;
    }
    catch (IOException e) {
      throw new GitException("Git command failed: " + e.getMessage(), e);
    }
    catch (InterruptedException e) {
      process.destroy();
      Thread.currentThread().interrupt();
      throw new GitException("Git command was interrupted", e);
    }
  }

  /** Runs git and returns stdout text. Throws when the exit code is not zero. */
  public String runOk(Path workDir, List<String> args, byte[] stdin) throws GitException {
    GitResult result = run(workDir, args, stdin);
    if (!result.ok()) {
      throw GitException.fromResult("git " + args.get(0), result);
    }
    return result.stdoutText();
  }

  /** Windows reports a missing executable as error 2 and a too long command line as error 206. */
  private static GitException startFailure(String executable, IOException e) {
    String reason = String.valueOf(e.getMessage());
    if (reason.contains("error=206")) {
      return new GitException("Git command line is too long: " + reason, e);
    }
    if (reason.contains("error=2,") || reason.contains("error=3,") || reason.contains("No such file")) {
      return new GitException("Git executable not found: " + executable + ". Set the path in Preferences → Gitbench.", e);
    }
    return new GitException("Cannot start git (" + executable + "): " + reason, e);
  }

  private static byte[] readAll(InputStream stream) {
    try (stream; ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
      stream.transferTo(buffer);
      return buffer.toByteArray();
    }
    catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
