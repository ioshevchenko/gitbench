package gitbench.model;

import gitbench.git.ChangeKind;
import gitbench.git.FileChange;
import gitbench.model.ChangesTreeNode.Root;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InclusionModelTest {
  private static FileChange change(String path, ChangeKind kind) {
    return new FileChange(path, null, kind);
  }

  @Test
  void newChangesIncludedByDefault() {
    InclusionModel model = new InclusionModel();
    List<FileChange> all = List.of(change("m.c", ChangeKind.MODIFIED), change("a.c", ChangeKind.ADDED),
                                   change("d.c", ChangeKind.DELETED), change("u.c", ChangeKind.UNVERSIONED));
    model.retainOnly(all);
    assertThat(model.included()).containsExactly(all.get(0), all.get(1), all.get(2));
  }

  @Test
  void partialStateForMixedDir() {
    InclusionModel model = new InclusionModel();
    List<FileChange> all = List.of(change("src/a.c", ChangeKind.MODIFIED), change("src/b.c", ChangeKind.MODIFIED));
    model.retainOnly(all);
    model.setIncluded(List.of(all.get(1)), false);
    Root root = ChangesTreeBuilder.build(all, true).get(0);
    assertThat(model.stateOf(root.children().get(0))).isEqualTo(InclusionModel.State.PARTIAL);
    assertThat(model.stateOf(root)).isEqualTo(InclusionModel.State.PARTIAL);
  }

  @Test
  void emptyRootIsOff() {
    assertThat(new InclusionModel().stateOf(new Root("Changes", List.of(), 0, false))).isEqualTo(InclusionModel.State.OFF);
  }

  @Test
  void retainOnlyDropsGoneFiles() {
    InclusionModel model = new InclusionModel();
    FileChange a = change("a.c", ChangeKind.MODIFIED);
    FileChange b = change("b.c", ChangeKind.MODIFIED);
    model.retainOnly(List.of(a, b));
    model.setIncluded(List.of(b), false);
    model.retainOnly(List.of(b));
    assertThat(model.isIncluded(b)).isFalse();
    assertThat(model.included()).isEmpty();
  }

  @Test
  void includedFollowsCurrentKindOfPath() {
    InclusionModel model = new InclusionModel();
    model.retainOnly(List.of(change("a.c", ChangeKind.UNVERSIONED)));
    model.setIncluded(List.of(change("a.c", ChangeKind.UNVERSIONED)), true);
    FileChange added = change("a.c", ChangeKind.ADDED);
    model.retainOnly(List.of(added));
    assertThat(model.included()).containsExactly(added);
  }
}
