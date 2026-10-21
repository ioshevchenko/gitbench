package gitbench.git;

import gitbench.testutil.TempRepo;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CommitServiceTest {
  private static final CommitOptions PLAIN = new CommitOptions(false, false, null);

  private final GitRunner runner = new GitRunner(() -> "git", GitCommandListener.NONE);
  private final CommitService commits = new CommitService(runner);
  private final StatusService status = new StatusService(runner);

  private static TempRepo repoWithAB() {
    TempRepo repo = TempRepo.init();
    repo.write("a.c", "a\n");
    repo.write("b.c", "b\n");
    repo.commitAll("init");
    return repo;
  }

  private static FileChange modified(String path) {
    return new FileChange(path, null, ChangeKind.MODIFIED);
  }

  @Test
  void commitsOnlySelectedFiles() throws Exception {
    TempRepo repo = repoWithAB();
    repo.write("a.c", "a2\n");
    repo.write("b.c", "b2\n");
    GitRepository r = new GitRepository(repo.root());
    commits.commit(r, "change a", List.of(modified("a.c")), PLAIN);
    assertThat(status.status(r)).containsExactly(modified("b.c"));
  }

  @Test
  void returnsShortHash() throws Exception {
    TempRepo repo = repoWithAB();
    repo.write("a.c", "a2\n");
    String hash = commits.commit(new GitRepository(repo.root()), "change a", List.of(modified("a.c")), PLAIN);
    assertThat(hash).isEqualTo(repo.git("rev-parse", "--short", "HEAD").trim());
  }

  @Test
  void ignoresManualStaging() throws Exception {
    TempRepo repo = repoWithAB();
    repo.write("a.c", "a2\n");
    repo.write("b.c", "b2\n");
    repo.git("add", "b.c");
    commits.commit(new GitRepository(repo.root()), "change a", List.of(modified("a.c")), PLAIN);
    assertThat(repo.git("show", "--name-only", "--format=", "HEAD").trim()).isEqualTo("a.c");
  }

  @Test
  void addsSelectedUnversionedFirst() throws Exception {
    TempRepo repo = repoWithAB();
    repo.write("new.c", "n\n");
    commits.commit(new GitRepository(repo.root()), "add new",
                   List.of(new FileChange("new.c", null, ChangeKind.UNVERSIONED)), PLAIN);
    assertThat(repo.git("show", "--name-only", "--format=", "HEAD").trim()).isEqualTo("new.c");
  }

  @Test
  void commitsRenameWithBothPaths() throws Exception {
    TempRepo repo = repoWithAB();
    repo.git("mv", "a.c", "renamed.c");
    GitRepository r = new GitRepository(repo.root());
    commits.commit(r, "rename", List.of(new FileChange("renamed.c", "a.c", ChangeKind.RENAMED)), PLAIN);
    assertThat(repo.git("show", "--name-status", "--format=", "HEAD").trim()).startsWith("R100");
    assertThat(status.status(r)).isEmpty();
  }

  @Test
  void commitsDeletedFile() throws Exception {
    TempRepo repo = repoWithAB();
    Files.delete(repo.root().resolve("a.c"));
    commits.commit(new GitRepository(repo.root()), "delete",
                   List.of(new FileChange("a.c", null, ChangeKind.DELETED)), PLAIN);
    assertThat(repo.git("ls-tree", "--name-only", "HEAD").trim()).isEqualTo("b.c");
  }

  @Test
  void amendReplacesLastCommit() throws Exception {
    TempRepo repo = repoWithAB();
    GitRepository r = new GitRepository(repo.root());
    repo.write("a.c", "a2\n");
    commits.commit(r, "first", List.of(modified("a.c")), PLAIN);
    repo.write("a.c", "a3\n");
    commits.commit(r, "amended", List.of(modified("a.c")), new CommitOptions(true, false, null));
    assertThat(repo.git("rev-list", "--count", "HEAD").trim()).isEqualTo("2");
    assertThat(commits.lastCommitMessage(r)).isEqualTo("amended");
  }

  @Test
  void amendWithoutSelectedFilesChangesOnlyMessage() throws Exception {
    TempRepo repo = repoWithAB();
    GitRepository r = new GitRepository(repo.root());
    commits.commit(r, "reworded", List.of(), new CommitOptions(true, false, null));
    assertThat(repo.git("rev-list", "--count", "HEAD").trim()).isEqualTo("1");
    assertThat(commits.lastCommitMessage(r)).isEqualTo("reworded");
  }

  @Test
  void firstCommitInEmptyRepository() throws Exception {
    TempRepo repo = TempRepo.init();
    repo.write("main.c", "int main;\n");
    GitRepository r = new GitRepository(repo.root());
    assertThat(commits.hasCommits(r)).isFalse();
    assertThat(commits.lastCommitMessage(r)).isNull();
    commits.commit(r, "initial", List.of(new FileChange("main.c", null, ChangeKind.UNVERSIONED)), PLAIN);
    assertThat(commits.hasCommits(r)).isTrue();
  }

  @Test
  void commitsFileWithSpaceAndCyrillicName() throws Exception {
    TempRepo repo = repoWithAB();
    repo.write("Core/Src/мой файл.c", "x\n");
    GitRepository r = new GitRepository(repo.root());
    commits.commit(r, "unicode", status.status(r), PLAIN);
    assertThat(status.status(r)).isEmpty();
  }

  @Test
  void commitsFileWithGlobCharactersLiterally() throws Exception {
    TempRepo repo = repoWithAB();
    repo.write("file[1].c", "x\n");
    repo.write("file1.c", "y\n");
    GitRepository r = new GitRepository(repo.root());
    commits.commit(r, "only brackets", List.of(new FileChange("file[1].c", null, ChangeKind.UNVERSIONED)), PLAIN);
    assertThat(repo.git("show", "--name-only", "--format=", "HEAD").trim()).isEqualTo("file[1].c");
  }

  @Test
  void keepsLinesStartingWithHash() throws Exception {
    TempRepo repo = repoWithAB();
    repo.write("a.c", "a2\n");
    GitRepository r = new GitRepository(repo.root());
    commits.commit(r, "#42 fix UART\n\n#43 also", List.of(modified("a.c")), PLAIN);
    assertThat(commits.lastCommitMessage(r)).isEqualTo("#42 fix UART\n\n#43 also");
  }

  @Test
  void signOffAddsTrailer() throws Exception {
    TempRepo repo = repoWithAB();
    repo.write("a.c", "a2\n");
    GitRepository r = new GitRepository(repo.root());
    commits.commit(r, "signed", List.of(modified("a.c")), new CommitOptions(false, true, null));
    assertThat(commits.lastCommitMessage(r)).contains("Signed-off-by: Test User <test@example.com>");
  }

  @Test
  void setsAuthor() throws Exception {
    TempRepo repo = repoWithAB();
    repo.write("a.c", "a2\n");
    commits.commit(new GitRepository(repo.root()), "by other", List.of(modified("a.c")),
                   new CommitOptions(false, false, "Other Person <other@example.com>"));
    assertThat(repo.git("log", "-1", "--format=%an <%ae>").trim()).isEqualTo("Other Person <other@example.com>");
  }

  @Test
  void keepsMultilineMessage() throws Exception {
    TempRepo repo = repoWithAB();
    repo.write("a.c", "a2\n");
    GitRepository r = new GitRepository(repo.root());
    commits.commit(r, "subject\n\nbody line", List.of(modified("a.c")), PLAIN);
    assertThat(commits.lastCommitMessage(r)).isEqualTo("subject\n\nbody line");
  }

  @Test
  void addToVcsStagesUnversioned() throws Exception {
    TempRepo repo = repoWithAB();
    repo.write("new.c", "n\n");
    GitRepository r = new GitRepository(repo.root());
    commits.addToVcs(r, List.of("new.c"));
    assertThat(status.status(r)).containsExactly(new FileChange("new.c", null, ChangeKind.ADDED));
  }
}
