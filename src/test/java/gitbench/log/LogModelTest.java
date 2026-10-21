package gitbench.log;

import com.intellij.vcs.log.graph.EdgePrintElement;
import gitbench.git.GitCommandListener;
import gitbench.git.GitRepository;
import gitbench.git.GitRunner;
import gitbench.testutil.TempRepo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LogModelTest {
  private static final long T = 1_700_000_000L;

  private final GitRunner runner = new GitRunner(() -> "git", GitCommandListener.NONE);

  private LogModel model(TempRepo repo, boolean intelliSort) throws Exception {
    GitRepository r = new GitRepository(repo.root());
    List<GitRef> refs = new RefsLoader(runner).load(r);
    return LogModel.build(new TopologyLoader(runner).load(r, refs), refs, intelliSort);
  }

  private static List<String> hashes(LogModel model) {
    List<String> result = new ArrayList<>();
    for (int i = 0; i < model.rowCount(); i++) {
      result.add(model.row(i).hash());
    }
    return result;
  }

  private static String rev(TempRepo repo, String rev) {
    return repo.git("rev-parse", rev).trim();
  }

  /** main: base(T) → m1(T+20) → merge(T+30); feature: base → f1(T+10). */
  private static TempRepo mergeRepo() {
    TempRepo repo = TempRepo.init();
    repo.write("a.c", "a\n");
    repo.commitAllAt("base", T);
    repo.git("switch", "-q", "-c", "feature");
    repo.write("f.c", "f\n");
    repo.commitAllAt("f1", T + 10);
    repo.git("switch", "-q", "main");
    repo.write("m.c", "m\n");
    repo.commitAllAt("m1", T + 20);
    repo.mergeAt("feature", T + 30);
    return repo;
  }

  @Test
  void rowsFollowTopoOrder() throws Exception {
    TempRepo repo = TempRepo.init();
    repo.write("a.c", "1\n");
    repo.commitAllAt("one", T);
    repo.write("a.c", "2\n");
    repo.commitAllAt("two", T + 1);
    repo.write("a.c", "3\n");
    repo.commitAllAt("three", T + 2);
    assertThat(hashes(model(repo, true))).containsExactly(rev(repo, "HEAD"), rev(repo, "HEAD~1"), rev(repo, "HEAD~2"));
  }

  @Test
  void intelliSortShowsIncomingCommitsBelowMerge() throws Exception {
    TempRepo repo = mergeRepo();
    assertThat(hashes(model(repo, true)))
      .containsExactly(rev(repo, "HEAD"), rev(repo, "feature"), rev(repo, "HEAD^1"), rev(repo, "HEAD~2"));
    assertThat(hashes(model(repo, false)))
      .containsExactly(rev(repo, "HEAD"), rev(repo, "HEAD^1"), rev(repo, "feature"), rev(repo, "HEAD~2"));
  }

  @Test
  void rowCarriesRefs() throws Exception {
    TempRepo repo = mergeRepo();
    LogModel model = model(repo, true);
    LogRow head = model.row(model.rowOf(rev(repo, "HEAD")));
    assertThat(head.refs()).extracting(GitRef::name).contains("main", "HEAD");
    assertThat(head.refs()).filteredOn(GitRef::current).extracting(GitRef::name).containsExactly("main");
  }

  @Test
  void filterByBranchHidesOtherBranches() throws Exception {
    TempRepo repo = mergeRepo();
    repo.git("switch", "-q", "-c", "side", "HEAD~2");
    repo.write("s.c", "s\n");
    repo.commitAllAt("side only", T + 40);
    LogModel full = model(repo, true);
    int featureHead = full.commitIndexOf(rev(repo, "feature"));
    LogModel onlyFeature = full.filtered(Set.of(featureHead), null);
    assertThat(hashes(onlyFeature)).containsExactly(rev(repo, "feature"), rev(repo, "main~2"));
  }

  @Test
  void filterByMatchedCommitsKeepsOnlyThem() throws Exception {
    TempRepo repo = mergeRepo();
    LogModel full = model(repo, true);
    int f1 = full.commitIndexOf(rev(repo, "feature"));
    assertThat(hashes(full.filtered(null, Set.of(f1)))).containsExactly(rev(repo, "feature"));
  }

  @Test
  void mergeRowHasTwoOutgoingEdges() throws Exception {
    TempRepo repo = mergeRepo();
    LogModel model = model(repo, true);
    LogRow merge = model.row(0);
    long down = merge.printElements().stream()
      .filter(e -> e instanceof EdgePrintElement edge && edge.getType() == EdgePrintElement.Type.DOWN)
      .count();
    assertThat(down).isEqualTo(2);
  }

  @Test
  void commitsOfOtherBranchesAreNotInCurrentBranch() throws Exception {
    TempRepo repo = mergeRepo();
    repo.git("switch", "-q", "-c", "side", "HEAD~2");
    repo.write("s.c", "s\n");
    repo.commitAllAt("side only", T + 40);
    repo.git("switch", "-q", "main");
    LogModel model = model(repo, true);
    assertThat(model.inCurrentBranch(model.commitIndexOf(rev(repo, "main")))).isTrue();
    assertThat(model.inCurrentBranch(model.commitIndexOf(rev(repo, "feature")))).isTrue();
    assertThat(model.inCurrentBranch(model.commitIndexOf(rev(repo, "side")))).isFalse();
  }

  @Test
  void rowOfUnknownHashIsMinusOne() throws Exception {
    assertThat(model(mergeRepo(), true).rowOf("0000000000000000000000000000000000000000")).isEqualTo(-1);
  }

  @Test
  void emptyRepositoryHasNoRows() throws Exception {
    assertThat(model(TempRepo.init(), true).rowCount()).isZero();
  }

  @Test
  void colorOfHeadFragmentIsRefNameHash() {
    GitRef main = new GitRef("main", "refs/heads/main", RefType.LOCAL_BRANCH, "h", null, true);
    HeadRefColors colors = new HeadRefColors(id -> id == 7 ? List.of(main) : List.of());
    assertThat(colors.getColor(7, 1, 1)).isEqualTo("main".hashCode());
    assertThat(colors.getColor(7, 1, 2)).isEqualTo(2);
    assertThat(colors.getColor(8, 3, 3)).isEqualTo(0);
  }
}
