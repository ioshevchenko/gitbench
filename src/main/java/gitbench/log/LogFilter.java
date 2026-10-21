package gitbench.log;

import java.time.LocalDate;
import java.util.List;

/**
 * The filters of the Git Log bar. Empty lists and {@code null} values mean "no filter".
 *
 * @param text     the "Text or hash" field
 * @param branches short ref names; empty means all branches
 * @param users    author names or emails; git matches any of them
 * @param paths    paths relative to the repository root
 */
public record LogFilter(String text, boolean regex, boolean matchCase, List<String> branches, List<String> users,
                        LocalDate since, LocalDate until, List<String> paths) {
  public LogFilter {
    branches = branches == null ? List.of() : List.copyOf(branches);
    users = users == null ? List.of() : List.copyOf(users);
    paths = paths == null ? List.of() : List.copyOf(paths);
  }

  public static LogFilter none() {
    return new LogFilter(null, false, false, List.of(), List.of(), null, null, List.of());
  }

  public boolean isEmpty() {
    return branches.isEmpty() && !hasCommitFilter();
  }

  /** True when a filter other than the branch filter is set. Only those need a git query. */
  public boolean hasCommitFilter() {
    return (text != null && !text.isBlank()) || !users.isEmpty() || since != null || until != null || !paths.isEmpty();
  }

  public LogFilter withText(String newText, boolean newRegex, boolean newMatchCase) {
    return new LogFilter(newText, newRegex, newMatchCase, branches, users, since, until, paths);
  }

  public LogFilter withBranches(List<String> newBranches) {
    return new LogFilter(text, regex, matchCase, newBranches, users, since, until, paths);
  }

  public LogFilter withUsers(List<String> newUsers) {
    return new LogFilter(text, regex, matchCase, branches, newUsers, since, until, paths);
  }

  public LogFilter withDates(LocalDate newSince, LocalDate newUntil) {
    return new LogFilter(text, regex, matchCase, branches, users, newSince, newUntil, paths);
  }

  public LogFilter withPaths(List<String> newPaths) {
    return new LogFilter(text, regex, matchCase, branches, users, since, until, newPaths);
  }
}
