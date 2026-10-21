package gitbench.merge;

import gitbench.git.CommitService;
import gitbench.git.GitException;
import gitbench.git.GitRepository;
import gitbench.git.GitResult;
import gitbench.git.GitRunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Lists the conflicted files of a repository and resolves them by taking one side, through the git CLI. */
public final class ConflictService {
  private static final int SHORT_HASH_LENGTH = 8;
  private static final int INDEX_LOCK_ATTEMPTS = 3;
  private static final int INDEX_LOCK_PAUSE_MS = 300;

  private final GitRunner runner;

  public ConflictService(GitRunner runner) {
    this.runner = runner;
  }

  public List<ConflictFile> list(GitRepository repo) throws GitException {
    GitResult status = runner.run(repo.root(), List.of("status", "--porcelain=v2", "-z", "--untracked-files=no"), null);
    if (!status.ok()) {
      throw GitException.fromResult("git status", status);
    }
    return parse(status.stdout());
  }

  /**
   * Reads the unmerged entries of {@code git status --porcelain=v2 -z}. A rename record "2 ..." is followed by its
   * original path as a separate NUL-terminated field, which is skipped, so a path such as "u old.c" is not taken for
   * an unmerged entry.
   */
  static List<ConflictFile> parse(byte[] porcelainV2z) {
    String[] records = new String(porcelainV2z, StandardCharsets.UTF_8).split("\0");
    List<ConflictFile> files = new ArrayList<>();
    for (int i = 0; i < records.length; i++) {
      String record = records[i];
      if (record.startsWith("2 ")) {
        i++;
      }
      else if (record.startsWith("u ")) {
        String[] fields = record.split(" ", 11);
        files.add(new ConflictFile(fields[10], ConflictType.fromXy(fields[1])));
      }
    }
    return files;
  }

  /**
   * Finishes the operation after the last conflict, as the IntelliJ notifications do: continues a rebase, a
   * cherry-pick or a revert, and commits a merge with its prepared message. No editor opens.
   */
  public void continueOperation(GitRepository repo, MergeContext context) throws GitException {
    List<String> command = switch (context.operation()) {
      case REBASE -> List.of("-c", "core.editor=true", "rebase", "--continue");
      case CHERRY_PICK -> List.of("-c", "core.editor=true", "cherry-pick", "--continue");
      case REVERT -> List.of("-c", "core.editor=true", "revert", "--continue");
      case MERGE -> List.of("commit", "--no-edit");
      case NONE -> List.of();
    };
    if (!command.isEmpty()) {
      retryOnIndexLock(() -> runner.runOk(repo.root(), command, null));
    }
  }

  /** Finds the operation in progress from the files git keeps in its directory while it waits for a resolution. */
  public MergeContext context(GitRepository repo) throws GitException {
    Path gitDir = Path.of(runner.runOk(repo.root(), List.of("rev-parse", "--absolute-git-dir"), null).trim());
    String branch = runner.run(repo.root(), List.of("symbolic-ref", "--short", "-q", "HEAD"), null).stdoutText().trim();
    for (String rebaseDir : List.of("rebase-merge", "rebase-apply")) {
      Path dir = gitDir.resolve(rebaseDir);
      // "git am" keeps its state in rebase-apply too, marked by the file "applying"; it is not a rebase.
      if (Files.isDirectory(dir) && !Files.exists(dir.resolve("applying"))) {
        String headName = read(dir.resolve("head-name")).replaceFirst("^refs/heads/", "");
        String onto = read(dir.resolve("onto"));
        return new MergeContext(MergeOperation.REBASE, headName, onto.isEmpty() ? "" : nameOf(repo, onto));
      }
    }
    if (Files.exists(gitDir.resolve("CHERRY_PICK_HEAD"))) {
      return new MergeContext(MergeOperation.CHERRY_PICK, branch, shortHash(read(gitDir.resolve("CHERRY_PICK_HEAD"))));
    }
    if (Files.exists(gitDir.resolve("REVERT_HEAD"))) {
      return new MergeContext(MergeOperation.REVERT, branch, shortHash(read(gitDir.resolve("REVERT_HEAD"))));
    }
    if (Files.exists(gitDir.resolve("MERGE_HEAD"))) {
      String hash = read(gitDir.resolve("MERGE_HEAD")).lines().findFirst().orElse("");
      return new MergeContext(MergeOperation.MERGE, branch, nameOf(repo, hash));
    }
    return new MergeContext(MergeOperation.NONE, branch, "");
  }

  /** Accept Yours: the file of the Yours column becomes the result. */
  public void acceptYours(GitRepository repo, List<ConflictFile> files, MergeContext context) throws GitException {
    accept(repo, files, !context.reversed());
  }

  /** Accept Theirs: the file of the Theirs column becomes the result. */
  public void acceptTheirs(GitRepository repo, List<ConflictFile> files, MergeContext context) throws GitException {
    accept(repo, files, context.reversed());
  }

  /** Marks the file resolved with its content on disk, as IntelliJ does after Apply in the merge window. */
  public void markResolved(GitRepository repo, String path) throws GitException {
    retryOnIndexLock(() -> CommitService.runPathspec(runner, repo, List.of("add", "-A"), List.of(path)));
  }

  private interface GitCall {
    void run() throws GitException;
  }

  /**
   * Runs the call again when another git process, such as the status of a view, holds index.lock for a moment.
   */
  private static void retryOnIndexLock(GitCall call) throws GitException {
    for (int attempt = 1; ; attempt++) {
      try {
        call.run();
        return;
      }
      catch (GitException e) {
        if (attempt >= INDEX_LOCK_ATTEMPTS || !String.valueOf(e.userMessage()).contains("index.lock")) {
          throw e;
        }
        try {
          Thread.sleep(INDEX_LOCK_PAUSE_MS);
        }
        catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          throw e;
        }
      }
    }
  }

  /** Takes git stage 2 ({@code ours}) or stage 3 for each file; a side without the file removes it. */
  private void accept(GitRepository repo, List<ConflictFile> files, boolean ours) throws GitException {
    List<String> remove = new ArrayList<>();
    List<String> keep = new ArrayList<>();
    for (ConflictFile file : files) {
      boolean deleted = ours ? file.oursDeleted() : file.theirsDeleted();
      (deleted ? remove : keep).add(file.path());
    }
    if (!keep.isEmpty()) {
      retryOnIndexLock(() -> CommitService.runPathspec(runner, repo, List.of("checkout", ours ? "--ours" : "--theirs"), keep));
      retryOnIndexLock(() -> CommitService.runPathspec(runner, repo, List.of("add", "-A"), keep));
    }
    if (!remove.isEmpty()) {
      retryOnIndexLock(() -> CommitService.runPathspec(runner, repo, List.of("rm", "-q", "--ignore-unmatch"), remove));
    }
  }

  /** A branch name for the commit, as IntelliJ shows it, or the short hash. */
  private String nameOf(GitRepository repo, String hash) throws GitException {
    GitResult name = runner.run(repo.root(), List.of("name-rev", "--name-only", "--no-undefined", "--refs=refs/heads/*",
                                                     "--refs=refs/remotes/*", "--exclude=refs/remotes/*/HEAD", hash), null);
    String text = name.ok() ? name.stdoutText().trim().replaceFirst("^remotes/", "") : "";
    return text.isEmpty() || text.contains("~") || text.contains("^") ? shortHash(hash) : text;
  }

  private static String shortHash(String hash) {
    String trimmed = hash.trim();
    return trimmed.length() > SHORT_HASH_LENGTH ? trimmed.substring(0, SHORT_HASH_LENGTH) : trimmed;
  }

  private static String read(Path file) throws GitException {
    try {
      return Files.exists(file) ? Files.readString(file).trim() : "";
    }
    catch (IOException e) {
      throw new GitException("Cannot read " + file + ": " + e.getMessage(), e);
    }
  }
}
