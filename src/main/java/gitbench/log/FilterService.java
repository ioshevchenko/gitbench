package gitbench.log;

import gitbench.git.GitException;
import gitbench.git.GitRepository;
import gitbench.git.GitResult;
import gitbench.git.GitRunner;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Finds the commits that match the Git Log filters with {@code git log}. */
public final class FilterService {
  private static final Pattern HASH_PREFIX = Pattern.compile("[0-9a-fA-F]{4,40}");
  private static final int MAX_PATHS = 100;

  private final GitRunner runner;

  public FilterService(GitRunner runner) {
    this.runner = runner;
  }

  /** Returns the full hashes of the matching commits, or {@code null} when no commit filter is set. */
  public Set<String> matching(GitRepository repo, LogFilter filter) throws GitException {
    if (!filter.hasCommitFilter()) {
      return null;
    }
    String text = filter.text() == null ? "" : filter.text().trim();
    boolean otherFilters = !filter.users().isEmpty() || filter.since() != null || filter.until() != null || !filter.paths().isEmpty();
    Set<String> result = new LinkedHashSet<>();
    if (text.isEmpty()) {
      result.addAll(log(repo, filter, List.of()));
      return result;
    }
    List<String> grep = new ArrayList<>();
    grep.add("--grep=" + text);
    if (!filter.matchCase()) {
      grep.add("-i");
    }
    grep.add(filter.regex() ? "-E" : "-F");
    result.addAll(log(repo, filter, grep));
    if (HASH_PREFIX.matcher(text).matches()) {
      Set<String> byHash = hashesWithPrefix(repo, text);
      if (otherFilters && !byHash.isEmpty()) {
        byHash.retainAll(new HashSet<>(log(repo, filter, List.of())));
      }
      result.addAll(byHash);
    }
    return result;
  }

  /** Returns the commits reachable from {@code includeRef} and not from {@code excludeRef}: Compare with Current. */
  public Set<String> range(GitRepository repo, String excludeRef, String includeRef) throws GitException {
    String out = runner.runOk(repo.root(), List.of("log", "--format=%H", includeRef, "--not", excludeRef, "--"), null);
    Set<String> result = new LinkedHashSet<>();
    out.lines().filter(line -> !line.isBlank()).forEach(result::add);
    return result;
  }

  private List<String> log(GitRepository repo, LogFilter filter, List<String> extra) throws GitException {
    List<String> args = new ArrayList<>(List.of("log", "--format=%H"));
    args.addAll(extra);
    for (String user : filter.users()) {
      args.add("--author=" + user);
    }
    if (filter.since() != null) {
      args.add("--since=" + filter.since() + " 00:00:00");
    }
    if (filter.until() != null) {
      args.add("--until=" + filter.until() + " 23:59:59");
    }
    if (filter.branches().isEmpty()) {
      args.addAll(List.of("--branches", "--remotes", "--tags", "HEAD"));
    }
    else {
      args.addAll(filter.branches());
    }
    // Always end the revisions with "--": a branch named like a folder (docs, test) is otherwise ambiguous.
    args.add("--");
    if (!filter.paths().isEmpty()) {
      args.addAll(filter.paths().subList(0, Math.min(MAX_PATHS, filter.paths().size())));
    }
    GitResult result = runner.run(repo.root(), args, null);
    if (!result.ok()) {
      // An empty repository has no HEAD: no commits match.
      if (result.stderr().contains("unknown revision") || result.stderr().contains("bad revision")) {
        return List.of();
      }
      throw gitbench.git.GitException.fromResult("git log", result);
    }
    return result.stdoutText().lines().filter(line -> !line.isBlank()).toList();
  }

  private Set<String> hashesWithPrefix(GitRepository repo, String prefix) throws GitException {
    GitResult result = runner.run(repo.root(), List.of("rev-parse", "--disambiguate=" + prefix.toLowerCase(java.util.Locale.ROOT)), null);
    Set<String> hashes = new LinkedHashSet<>();
    if (result.ok()) {
      result.stdoutText().lines().filter(line -> !line.isBlank()).forEach(hashes::add);
    }
    return hashes;
  }
}
