package gitbench.log;

import gitbench.git.GitCommandListener;
import gitbench.git.GitRepository;
import gitbench.git.GitRunner;
import gitbench.testutil.TempRepo;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FilterServiceTest {
  private static final long T = 1_700_000_000L;
  private static final long DAY = 86_400L;

  private final FilterService service = new FilterService(new GitRunner(() -> "git", GitCommandListener.NONE));

  /** Three commits three days apart: "Add UART driver" (T), "fix: uart baud" by Other (T+3d) in Core/, "Docs" (T+6d). */
  private static TempRepo repo() {
    TempRepo repo = TempRepo.init();
    repo.write("a.c", "a\n");
    repo.commitAllAt("Add UART driver", T);
    repo.write("Core/uart.c", "u\n");
    repo.git("add", "-A");
    repo.gitAt(T + 3 * DAY, "commit", "-q", "-m", "fix: uart baud", "--author=Other Person <other@example.com>");
    repo.write("README.md", "r\n");
    repo.commitAllAt("Docs", T + 6 * DAY);
    return repo;
  }

  private static LogFilter text(String text, boolean regex, boolean matchCase) {
    return new LogFilter(text, regex, matchCase, List.of(), List.of(), null, null, List.of());
  }

  private static String rev(TempRepo repo, String rev) {
    return repo.git("rev-parse", rev).trim();
  }

  private static LocalDate dayOf(long epochSeconds) {
    return Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).toLocalDate();
  }

  @Test
  void textMatchesSubjectIgnoringCase() throws Exception {
    TempRepo repo = repo();
    assertThat(service.matching(new GitRepository(repo.root()), text("uart", false, false)))
      .containsExactlyInAnyOrder(rev(repo, "HEAD~2"), rev(repo, "HEAD~1"));
  }

  @Test
  void matchCaseRespected() throws Exception {
    TempRepo repo = repo();
    assertThat(service.matching(new GitRepository(repo.root()), text("UART", false, true)))
      .containsExactly(rev(repo, "HEAD~2"));
  }

  @Test
  void regexMatches() throws Exception {
    TempRepo repo = repo();
    assertThat(service.matching(new GitRepository(repo.root()), text("^fix:.*baud$", true, false)))
      .containsExactly(rev(repo, "HEAD~1"));
  }

  @Test
  void plainTextIsNotRegex() throws Exception {
    TempRepo repo = repo();
    assertThat(service.matching(new GitRepository(repo.root()), text("fix:.*", false, false))).isEmpty();
  }

  @Test
  void hashPrefixMatches() throws Exception {
    TempRepo repo = repo();
    String hash = rev(repo, "HEAD~1");
    assertThat(service.matching(new GitRepository(repo.root()), text(hash.substring(0, 7), false, false))).contains(hash);
  }

  @Test
  void userFilter() throws Exception {
    TempRepo repo = repo();
    LogFilter filter = new LogFilter(null, false, false, List.of(), List.of("Other Person"), null, null, List.of());
    assertThat(service.matching(new GitRepository(repo.root()), filter)).containsExactly(rev(repo, "HEAD~1"));
  }

  @Test
  void dateRange() throws Exception {
    TempRepo repo = repo();
    LogFilter filter = new LogFilter(null, false, false, List.of(), List.of(), dayOf(T + 2 * DAY), dayOf(T + 4 * DAY), List.of());
    assertThat(service.matching(new GitRepository(repo.root()), filter)).containsExactly(rev(repo, "HEAD~1"));
  }

  @Test
  void pathFilter() throws Exception {
    TempRepo repo = repo();
    LogFilter filter = new LogFilter(null, false, false, List.of(), List.of(), null, null, List.of("Core"));
    assertThat(service.matching(new GitRepository(repo.root()), filter)).containsExactly(rev(repo, "HEAD~1"));
  }

  @Test
  void combinedFiltersIntersect() throws Exception {
    TempRepo repo = repo();
    LogFilter filter = new LogFilter("uart", false, false, List.of(), List.of("Test User"), null, null, List.of());
    assertThat(service.matching(new GitRepository(repo.root()), filter)).containsExactly(rev(repo, "HEAD~2"));
  }

  @Test
  void branchRestrictsTheSearch() throws Exception {
    TempRepo repo = repo();
    repo.git("switch", "-q", "-c", "side", "HEAD~2");
    repo.write("s.c", "s\n");
    repo.commitAllAt("uart on side", T + 7 * DAY);
    LogFilter filter = new LogFilter("uart", false, false, List.of("main"), List.of(), null, null, List.of());
    assertThat(service.matching(new GitRepository(repo.root()), filter)).doesNotContain(rev(repo, "side"));
  }

  @Test
  void rangeListsCommitsOfBranchNotInCurrent() throws Exception {
    TempRepo repo = repo();
    repo.git("switch", "-q", "-c", "side", "HEAD~1");
    repo.write("s.c", "s\n");
    repo.commitAllAt("side work", T + 7 * DAY);
    assertThat(service.range(new GitRepository(repo.root()), "main", "side")).containsExactly(rev(repo, "side"));
    assertThat(service.range(new GitRepository(repo.root()), "side", "main")).containsExactly(rev(repo, "main"));
  }

  @Test
  void noCommitFilterReturnsNull() throws Exception {
    TempRepo repo = repo();
    LogFilter onlyBranch = new LogFilter(null, false, false, List.of("main"), List.of(), null, null, List.of());
    assertThat(onlyBranch.hasCommitFilter()).isFalse();
    assertThat(service.matching(new GitRepository(repo.root()), onlyBranch)).isNull();
    assertThat(LogFilter.none().isEmpty()).isTrue();
  }
}
