package gitbench.git;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Commits the selected files and ignores manual staging, as the IntelliJ Commit window does without the Staging Area. */
public final class CommitService {
  private final GitRunner runner;

  public CommitService(GitRunner runner) {
    this.runner = runner;
  }

  /** Commits {@code selected} with {@code message}. Returns the short hash of the new commit. */
  public String commit(GitRepository repo, String message, List<FileChange> selected, CommitOptions opts) throws GitException {
    List<String> paths = List.copyOf(pathsOf(selected));
    stage(repo, selected);
    List<String> args = new ArrayList<>(List.of("commit", "--only"));
    if (opts.amend()) {
      args.add("--amend");
    }
    if (opts.signOff()) {
      args.add("--signoff");
    }
    if (opts.author() != null && !opts.author().isBlank()) {
      args.add("--author=" + opts.author());
    }
    args.add("--cleanup=whitespace");
    Path messageFile = null;
    try {
      messageFile = Files.createTempFile("gitbench-commit", ".txt");
      Files.writeString(messageFile, message, StandardCharsets.UTF_8);
      args.add("-F");
      args.add(messageFile.toString());
      if (paths.isEmpty()) {
        runner.runOk(repo.root(), args, null);
      }
      else {
        runPathspec(repo, args, paths);
      }
    }
    catch (IOException e) {
      throw new GitException("Cannot write the commit message: " + e.getMessage(), e);
    }
    finally {
      if (messageFile != null) {
        try {
          Files.deleteIfExists(messageFile);
        }
        catch (IOException ignored) {
          // A leftover file in the temp folder harms nothing.
        }
      }
    }
    return runner.runOk(repo.root(), List.of("rev-parse", "--short", "HEAD"), null).trim();
  }

  /** Returns the full message of HEAD, or {@code null} when the repository has no commits. */
  public String lastCommitMessage(GitRepository repo) throws GitException {
    if (!hasCommits(repo)) {
      return null;
    }
    return runner.runOk(repo.root(), List.of("log", "-1", "--format=%B"), null).strip();
  }

  public boolean hasCommits(GitRepository repo) throws GitException {
    return runner.run(repo.root(), List.of("rev-parse", "--verify", "--quiet", "HEAD"), null).ok();
  }

  /** Adds unversioned files to the index. */
  public void addToVcs(GitRepository repo, List<String> paths) throws GitException {
    if (!paths.isEmpty()) {
      runPathspec(repo, List.of("add"), paths);
    }
  }

  /**
   * Puts the selected files into the index, so that {@code commit --only} also sees new files.
   * A gone path (a deleted file, the old path of a rename) can be absent from the index already,
   * so it goes through {@code rm --ignore-unmatch}.
   */
  private void stage(GitRepository repo, List<FileChange> selected) throws GitException {
    List<String> present = new ArrayList<>();
    List<String> gone = new ArrayList<>();
    for (FileChange change : selected) {
      (change.kind() == ChangeKind.DELETED ? gone : present).add(change.path());
      if (change.origPath() != null) {
        gone.add(change.origPath());
      }
    }
    if (!present.isEmpty()) {
      runPathspec(repo, List.of("add", "-A"), present);
    }
    if (!gone.isEmpty()) {
      runPathspec(repo, List.of("rm", "--cached", "--ignore-unmatch", "-q"), gone);
    }
  }

  /**
   * Runs a git command on many paths. The paths go through stdin, because Windows limits the command line
   * to 32 767 characters and a CubeMX project easily has more.
   */
  private void runPathspec(GitRepository repo, List<String> args, List<String> paths) throws GitException {
    runPathspec(runner, repo, args, paths);
  }

  static void runPathspec(GitRunner runner, GitRepository repo, List<String> args, List<String> paths) throws GitException {
    List<String> all = new ArrayList<>(args);
    all.add("--pathspec-from-file=-");
    all.add("--pathspec-file-nul");
    runner.runOk(repo.root(), all, nulSeparated(paths));
  }

  private static byte[] nulSeparated(List<String> paths) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (String path : paths) {
      out.writeBytes(path.getBytes(StandardCharsets.UTF_8));
      out.write(0);
    }
    return out.toByteArray();
  }

  private static Set<String> pathsOf(List<FileChange> changes) {
    Set<String> paths = new LinkedHashSet<>();
    for (FileChange change : changes) {
      paths.add(change.path());
      if (change.origPath() != null) {
        paths.add(change.origPath());
      }
    }
    return paths;
  }
}
