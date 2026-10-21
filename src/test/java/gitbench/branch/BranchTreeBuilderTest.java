package gitbench.branch;

import gitbench.branch.BranchTreeNode.Branch;
import gitbench.branch.BranchTreeNode.Folder;
import gitbench.branch.BranchTreeNode.Group;
import gitbench.branch.BranchTreeNode.Head;
import gitbench.branch.BranchTreeNode.Remote;
import gitbench.git.GitCommandListener;
import gitbench.git.GitRepository;
import gitbench.git.GitRunner;
import gitbench.log.GitRef;
import gitbench.log.RefType;
import gitbench.testutil.TempRepo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BranchTreeBuilderTest {
  private static GitRef local(String name, boolean current) {
    return new GitRef(name, "refs/heads/" + name, RefType.LOCAL_BRANCH, "h" + name, null, current);
  }

  private static GitRef remote(String name) {
    return new GitRef(name, "refs/remotes/" + name, RefType.REMOTE_BRANCH, "r" + name, null, false);
  }

  private static final GitRef HEAD = new GitRef("HEAD", "HEAD", RefType.HEAD, "abcdef1234567890", null, false);

  private static List<BranchTreeNode> build(List<GitRef> refs, Set<String> favorites, String filter) {
    return BranchTreeBuilder.build(refs, favorites, Map.of(), filter, false, Set.of());
  }

  private static List<String> labels(List<BranchTreeNode> nodes) {
    return nodes.stream().map(BranchTreeBuilder::labelOf).toList();
  }

  @Test
  void headCurrentLocalRemoteOrder() {
    List<BranchTreeNode> roots = build(List.of(HEAD, local("main", true), local("dev", false), remote("origin/main")), Set.of(), null);
    assertThat(roots).hasSize(3);
    assertThat(((Head)roots.get(0)).label()).isEqualTo("HEAD (Current Branch)");
    Group localGroup = (Group)roots.get(1);
    assertThat(localGroup.title()).isEqualTo("Local");
    assertThat(labels(localGroup.children())).containsExactly("dev", "main");
    Group remoteGroup = (Group)roots.get(2);
    assertThat(remoteGroup.title()).isEqualTo("Remote");
    Remote origin = (Remote)remoteGroup.children().get(0);
    assertThat(origin.name()).isEqualTo("origin");
    assertThat(labels(origin.children())).containsExactly("main");
  }

  @Test
  void groupsSlashedNamesIntoFolders() {
    List<BranchTreeNode> roots = build(List.of(HEAD, local("main", true), local("feature/uart/фикс", false)), Set.of(), null);
    Group localGroup = (Group)roots.get(1);
    Folder feature = (Folder)localGroup.children().stream().filter(n -> n instanceof Folder).findFirst().orElseThrow();
    assertThat(feature.name()).isEqualTo("feature");
    Folder uart = (Folder)feature.children().get(0);
    assertThat(uart.name()).isEqualTo("uart");
    Branch branch = (Branch)uart.children().get(0);
    assertThat(branch.label()).isEqualTo("фикс");
    assertThat(branch.ref().name()).isEqualTo("feature/uart/фикс");
  }

  @Test
  void favoritesOnTop() {
    List<BranchTreeNode> roots = build(List.of(HEAD, local("alpha", false), local("zeta", true)), Set.of("zeta"), null);
    Group localGroup = (Group)roots.get(1);
    assertThat(labels(localGroup.children())).containsExactly("zeta", "alpha");
    assertThat(((Branch)localGroup.children().get(0)).favorite()).isTrue();
  }

  @Test
  void filterKeepsMatchingBranchesAndTheirFolders() {
    List<BranchTreeNode> roots = build(List.of(HEAD, local("main", true), local("feature/uart", false), local("feature/spi", false),
                                               remote("origin/main")), Set.of(), "UART");
    assertThat(roots).hasSize(2);
    Group localGroup = (Group)roots.get(1);
    Folder feature = (Folder)localGroup.children().get(0);
    assertThat(labels(feature.children())).containsExactly("uart");
  }

  @Test
  void onlyMineKeepsMyBranches() {
    List<BranchTreeNode> roots = BranchTreeBuilder.build(List.of(HEAD, local("main", true), local("mine", false), local("theirs", false)),
                                                         Set.of(), Map.of(), null, true, Set.of("mine"));
    assertThat(labels(((Group)roots.get(1)).children())).containsExactly("mine");
  }

  @Test
  void countsAreAttached() {
    List<BranchTreeNode> roots = BranchTreeBuilder.build(List.of(HEAD, local("main", true)), Set.of(),
                                                         Map.of("main", new AheadBehind.Counts(2, 1)), null, false, Set.of());
    Branch main = (Branch)((Group)roots.get(1)).children().get(0);
    assertThat(main.counts()).isEqualTo(new AheadBehind.Counts(2, 1));
  }

  @Test
  void detachedHeadIsShownByHash() {
    List<BranchTreeNode> roots = build(List.of(HEAD, local("main", false)), Set.of(), null);
    assertThat(((Head)roots.get(0)).label()).isEqualTo("HEAD (detached at abcdef1)");
  }

  @Test
  void myBranchesByLastCommitAuthor() throws Exception {
    TempRepo repo = TempRepo.init();
    repo.write("a.c", "a\n");
    repo.commitAll("mine");
    repo.git("switch", "-q", "-c", "other");
    repo.write("b.c", "b\n");
    repo.git("add", "-A");
    repo.git("commit", "-q", "-m", "theirs", "--author=Someone Else <else@example.com>");
    GitRunner runner = new GitRunner(() -> "git", GitCommandListener.NONE);
    assertThat(BranchTreeBuilder.myBranches(runner, new GitRepository(repo.root()))).containsExactly("main");
  }
}
