package gitbench.git;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Lists outgoing commits and pushes the current branch, as the IntelliJ Push dialog does. */
public final class PushService {
  private final GitRunner runner;

  public PushService(GitRunner runner) {
    this.runner = runner;
  }

  /** The upstream of the current branch, or the only remote (or {@code origin}) with the same branch name. */
  public PushTarget defaultTarget(GitRepository repo) throws GitException {
    return defaultTarget(repo, runner.runOk(repo.root(), List.of("symbolic-ref", "--short", "HEAD"), null).trim());
  }

  /** Like {@link #defaultTarget(GitRepository)}, for any local branch. */
  public PushTarget defaultTarget(GitRepository repo, String branch) throws GitException {
    GitResult remote = runner.run(repo.root(), List.of("config", "--get", "branch." + branch + ".remote"), null);
    GitResult merge = runner.run(repo.root(), List.of("config", "--get", "branch." + branch + ".merge"), null);
    if (remote.ok() && merge.ok()) {
      String target = merge.stdoutText().trim().replaceFirst("^refs/heads/", "");
      return new PushTarget(remote.stdoutText().trim(), target, true);
    }
    List<String> remotes = runner.runOk(repo.root(), List.of("remote"), null).lines().map(String::trim)
      .filter(s -> !s.isEmpty()).toList();
    String chosen = remotes.contains("origin") || remotes.isEmpty() ? "origin" : remotes.get(0);
    return new PushTarget(chosen, branch, false);
  }

  /** Returns the commits that the push sends, newest first. */
  public List<OutgoingCommit> outgoing(GitRepository repo, PushTarget target) throws GitException {
    return outgoing(repo, target, "HEAD");
  }

  /** Returns the commits that a push of {@code source} sends, newest first. */
  public List<OutgoingCommit> outgoing(GitRepository repo, PushTarget target, String source) throws GitException {
    List<String> args = new ArrayList<>(List.of("log", "--format=%h%x00%s%x00%an%x00%at"));
    if (target.hasUpstream()) {
      args.add(source + "@{u}.." + source);
    }
    else {
      args.add(source);
      args.add("--not");
      args.add("--remotes=" + target.remote());
    }
    List<OutgoingCommit> commits = new ArrayList<>();
    for (String line : runner.runOk(repo.root(), args, null).split("\n")) {
      String[] f = line.split("\0", -1);
      if (f.length == 4) {
        commits.add(new OutgoingCommit(f[0], f[1], f[2], Instant.ofEpochSecond(Long.parseLong(f[3].trim()))));
      }
    }
    return commits;
  }

  /** Returns the files that a commit changes, for the right side of the Push dialog. */
  public List<FileChange> changedFiles(GitRepository repo, String hash) throws GitException {
    GitResult result = runner.run(repo.root(), List.of("show", "--name-status", "-z", "--format=", hash), null);
    if (!result.ok()) {
      throw GitException.fromResult("git show", result);
    }
    String[] f = new String(result.stdout(), StandardCharsets.UTF_8).split("\0");
    List<FileChange> files = new ArrayList<>();
    for (int i = 0; i < f.length; i++) {
      String status = f[i].trim();
      if (status.isEmpty()) {
        continue;
      }
      switch (status.charAt(0)) {
        case 'R', 'C' -> {
          String from = f[++i];
          files.add(new FileChange(f[++i], from, ChangeKind.RENAMED));
        }
        case 'A' -> files.add(new FileChange(f[++i], null, ChangeKind.ADDED));
        case 'D' -> files.add(new FileChange(f[++i], null, ChangeKind.DELETED));
        default -> files.add(new FileChange(f[++i], null, ChangeKind.MODIFIED));
      }
    }
    return files;
  }

  /** Pushes HEAD to the target branch. Sets the upstream on the first push of a branch. */
  public PushResult push(GitRepository repo, PushTarget target) throws GitException {
    return push(repo, target, "HEAD");
  }

  /** Pushes {@code sourceBranch} (or {@code HEAD}) to the target branch; sets the upstream on its first push. */
  public PushResult push(GitRepository repo, PushTarget target, String sourceBranch) throws GitException {
    List<String> remotes = runner.runOk(repo.root(), List.of("remote"), null).lines().map(String::trim).toList();
    if (!remotes.contains(target.remote())) {
      return new PushResult(PushResult.Status.ERROR, "No remote '" + target.remote() + "' is configured.");
    }
    List<String> args = new ArrayList<>(List.of("push", "--porcelain"));
    if (!target.hasUpstream()) {
      args.add("-u");
    }
    args.add(target.remote());
    args.add(sourceBranch + ":refs/heads/" + target.branch());
    GitResult result = runner.run(repo.root(), args, null);
    for (String line : result.stdoutText().split("\n")) {
      String[] f = line.split("\t");
      if (f.length >= 3 && f[0].length() == 1) {
        String summary = f[2].trim();
        return switch (f[0].charAt(0)) {
          case '!' -> new PushResult(PushResult.Status.REJECTED, summary + " " + result.stderr().trim());
          case '=' -> new PushResult(PushResult.Status.UP_TO_DATE, "Everything is up to date.");
          default -> new PushResult(PushResult.Status.OK, summary);
        };
      }
    }
    String details = result.stderr().isBlank() ? result.stdoutText() : result.stderr();
    return new PushResult(result.ok() ? PushResult.Status.OK : PushResult.Status.ERROR, details.trim());
  }
}
