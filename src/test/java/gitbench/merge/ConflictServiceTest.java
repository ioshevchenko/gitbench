package gitbench.merge;

import gitbench.git.GitCommandListener;
import gitbench.git.GitRepository;
import gitbench.git.GitRunner;
import gitbench.testutil.TempRepo;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConflictServiceTest {
  private final ConflictService service = new ConflictService(new GitRunner(() -> "git", GitCommandListener.NONE));

  /** main changes a.c to "ours", feature changes it to "theirs"; HEAD is main. */
  static TempRepo divergedRepo() {
    TempRepo repo = TempRepo.init();
    repo.write("a.c", "base\n");
    repo.commitAll("base");
    repo.git("checkout", "-q", "-b", "feature");
    repo.write("a.c", "theirs\n");
    repo.commitAll("theirs");
    repo.git("checkout", "-q", "main");
    repo.write("a.c", "ours\n");
    repo.commitAll("ours");
    return repo;
  }

  static TempRepo mergeConflict() {
    TempRepo repo = divergedRepo();
    repo.gitMayFail("merge", "-q", "feature");
    return repo;
  }

  @Test
  void listsBothModifiedConflict() throws Exception {
    TempRepo repo = mergeConflict();

    assertThat(service.list(new GitRepository(repo.root()))).containsExactly(new ConflictFile("a.c", ConflictType.BOTH_MODIFIED));
  }

  @Test
  void noConflictsGivesEmptyList() throws Exception {
    TempRepo repo = divergedRepo();

    assertThat(service.list(new GitRepository(repo.root()))).isEmpty();
    assertThat(service.context(new GitRepository(repo.root())).operation()).isEqualTo(MergeOperation.NONE);
  }

  @Test
  void listsDeletedByThem() throws Exception {
    TempRepo repo = TempRepo.init();
    repo.write("a.c", "base\n");
    repo.commitAll("base");
    repo.git("checkout", "-q", "-b", "feature");
    repo.git("rm", "-q", "a.c");
    repo.commitAll("delete");
    repo.git("checkout", "-q", "main");
    repo.write("a.c", "ours\n");
    repo.commitAll("ours");
    repo.gitMayFail("merge", "-q", "feature");

    List<ConflictFile> files = service.list(new GitRepository(repo.root()));

    assertThat(files).containsExactly(new ConflictFile("a.c", ConflictType.DELETED_BY_THEM));
    assertThat(files.get(0).theirsDeleted()).isTrue();
    assertThat(files.get(0).canMerge()).isFalse();
  }

  @Test
  void mergeContextNamesBranches() throws Exception {
    TempRepo repo = mergeConflict();

    MergeContext context = service.context(new GitRepository(repo.root()));

    assertThat(context.operation()).isEqualTo(MergeOperation.MERGE);
    assertThat(context.reversed()).isFalse();
    assertThat(context.description()).isEqualTo("Merging branch feature into branch main");
    assertThat(context.yoursTitle()).isEqualTo("Your version, branch main");
    assertThat(context.theirsTitle()).isEqualTo("Changes from feature");
  }

  @Test
  void cherryPickContext() throws Exception {
    TempRepo repo = divergedRepo();
    String pick = repo.git("rev-parse", "feature").trim();
    repo.gitMayFail("cherry-pick", pick);

    MergeContext context = service.context(new GitRepository(repo.root()));

    assertThat(context.operation()).isEqualTo(MergeOperation.CHERRY_PICK);
    assertThat(context.yoursTitle()).isEqualTo("Local Changes");
    assertThat(context.theirsTitle()).isEqualTo("Changes from cherry-pick " + pick.substring(0, 8));
    assertThat(context.description()).isEqualTo("Conflicts during cherry-picking commit " + pick.substring(0, 8));
  }

  @Test
  void rebaseContextIsReversed() throws Exception {
    TempRepo repo = divergedRepo();
    repo.git("checkout", "-q", "feature");
    repo.gitMayFail("rebase", "main");

    MergeContext context = service.context(new GitRepository(repo.root()));

    assertThat(context.operation()).isEqualTo(MergeOperation.REBASE);
    assertThat(context.reversed()).isTrue();
    assertThat(context.description()).isEqualTo("Rebasing branch feature onto main");
  }

  @Test
  void acceptYoursKeepsOurContentAndStages() throws Exception {
    TempRepo repo = mergeConflict();
    GitRepository repository = new GitRepository(repo.root());

    service.acceptYours(repository, service.list(repository), service.context(repository));

    assertThat(Files.readString(repo.root().resolve("a.c"))).isEqualTo("ours\n");
    assertThat(service.list(repository)).isEmpty();
  }

  @Test
  void acceptTheirsInRebaseTakesUpstream() throws Exception {
    TempRepo repo = divergedRepo();
    repo.git("checkout", "-q", "feature");
    repo.gitMayFail("rebase", "main");
    GitRepository repository = new GitRepository(repo.root());

    service.acceptTheirs(repository, service.list(repository), service.context(repository));

    // In a rebase "Theirs" is the branch the commits go onto: main has "ours".
    assertThat(Files.readString(repo.root().resolve("a.c"))).isEqualTo("ours\n");
    assertThat(service.list(repository)).isEmpty();
  }

  @Test
  void acceptDeletedSideRemovesFile() throws Exception {
    TempRepo repo = TempRepo.init();
    repo.write("a.c", "base\n");
    repo.commitAll("base");
    repo.git("checkout", "-q", "-b", "feature");
    repo.git("rm", "-q", "a.c");
    repo.commitAll("delete");
    repo.git("checkout", "-q", "main");
    repo.write("a.c", "ours\n");
    repo.commitAll("ours");
    repo.gitMayFail("merge", "-q", "feature");
    GitRepository repository = new GitRepository(repo.root());

    service.acceptTheirs(repository, service.list(repository), service.context(repository));

    assertThat(repo.root().resolve("a.c")).doesNotExist();
    assertThat(service.list(repository)).isEmpty();
  }

  @Test
  void markResolvedStagesFile() throws Exception {
    TempRepo repo = mergeConflict();
    GitRepository repository = new GitRepository(repo.root());
    repo.write("a.c", "merged\n");

    service.markResolved(repository, "a.c");

    assertThat(service.list(repository)).isEmpty();
  }
}
