package gitbench.model;

import gitbench.git.ChangeKind;
import gitbench.git.FileChange;
import gitbench.model.ChangesTreeNode.Dir;
import gitbench.model.ChangesTreeNode.File;
import gitbench.model.ChangesTreeNode.Root;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChangesTreeBuilderTest {
  private static FileChange mod(String path) {
    return new FileChange(path, null, ChangeKind.MODIFIED);
  }

  private static FileChange unv(String path) {
    return new FileChange(path, null, ChangeKind.UNVERSIONED);
  }

  @Test
  void alwaysHasChangesRoot() {
    assertThat(ChangesTreeBuilder.build(List.of(), true))
      .containsExactly(new Root("Changes", List.of(), 0, false));
  }

  @Test
  void splitsUnversioned() {
    List<Root> roots = ChangesTreeBuilder.build(List.of(unv("n.c"), mod("a.c")), true);
    assertThat(roots).extracting(Root::title).containsExactly("Changes", "Unversioned Files");
    assertThat(roots.get(0).fileCount()).isEqualTo(1);
    assertThat(roots.get(1).unversioned()).isTrue();
  }

  @Test
  void flatWhenNotGrouped() {
    List<Root> roots = ChangesTreeBuilder.build(List.of(mod("x/b.c"), mod("y/A.c")), false);
    assertThat(roots.get(0).children()).containsExactly(new File(mod("y/A.c")), new File(mod("x/b.c")));
  }

  @Test
  void compactsSingleChildDirs() {
    List<Root> roots = ChangesTreeBuilder.build(List.of(mod("Core/Src/a.c"), mod("Core/Src/b.c")), true);
    assertThat(roots.get(0).children()).hasSize(1);
    Dir dir = (Dir)roots.get(0).children().get(0);
    assertThat(dir.label()).isEqualTo("Core/Src");
    assertThat(dir.path()).isEqualTo("Core/Src");
    assertThat(dir.children()).containsExactly(new File(mod("Core/Src/a.c")), new File(mod("Core/Src/b.c")));
  }

  @Test
  void keepsBranchingDirs() {
    List<Root> roots = ChangesTreeBuilder.build(List.of(mod("Core/Src/a.c"), mod("Core/Inc/a.h")), true);
    Dir core = (Dir)roots.get(0).children().get(0);
    assertThat(core.label()).isEqualTo("Core");
    assertThat(core.children()).extracting(n -> ((Dir)n).label()).containsExactly("Inc", "Src");
  }

  @Test
  void dirsBeforeFiles() {
    List<Root> roots = ChangesTreeBuilder.build(List.of(mod("a.c"), mod("src/z.c")), true);
    assertThat(roots.get(0).children().get(0)).isInstanceOf(Dir.class);
    assertThat(roots.get(0).children().get(1)).isEqualTo(new File(mod("a.c")));
  }

  @Test
  void filesInRootStayInRoot() {
    List<Root> roots = ChangesTreeBuilder.build(List.of(mod("a.c")), true);
    assertThat(roots.get(0).children()).containsExactly(new File(mod("a.c")));
  }
}
