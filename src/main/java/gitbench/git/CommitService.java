package gitbench.git;

import java.nio.charset.StandardCharsets;
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
    args.add("--cleanup=strip");
    args.add("-F");
    args.add("-");
    runner.runOk(repo.root(), withPaths(args, paths), message.getBytes(StandardCharsets.UTF_8));
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
      runner.runOk(repo.root(), withPaths(List.of("add"), paths), null);
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
      runner.runOk(repo.root(), withPaths(List.of("add", "-A"), present), null);
    }
    if (!gone.isEmpty()) {
      runner.runOk(repo.root(), withPaths(List.of("rm", "--cached", "--ignore-unmatch", "-q"), gone), null);
    }
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

  static List<String> withPaths(List<String> args, List<String> paths) {
    List<String> all = new ArrayList<>(args);
    all.add("--");
    all.addAll(paths);
    return all;
  }
}
