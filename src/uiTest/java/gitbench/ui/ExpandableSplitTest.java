package gitbench.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ToolBar;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExpandableSplitTest extends SwtTest {
  private ExpandableSplit split;
  private ToolBar toolbar;
  private Composite panel;
  private Label main;
  private final List<Boolean> changes = new ArrayList<>();

  private void create() {
    split = new ExpandableSplit(shell, GitbenchIcons.Icon.CHEVRON_RIGHT, "Show Branches", changes::add);
    toolbar = new ToolBar(split.controls(), SWT.VERTICAL | SWT.FLAT);
    panel = new Composite(split.sash(), SWT.NONE);
    main = new Label(split.sash(), SWT.NONE);
    split.setContents(toolbar, panel, main, 30);
    show();
  }

  @Test
  void anExpandedSplitShowsThePanelAndItsToolbar() {
    create();

    assertThat(split.expanded()).isTrue();
    assertThat(panel.isVisible()).isTrue();
    assertThat(toolbar.isVisible()).isTrue();
    assertThat(split.strip().isVisible()).isFalse();
    assertThat(split.sash().getWeights()[0]).isBetween(290, 310);
  }

  @Test
  void collapsingHidesThePanelAndShowsTheStrip() {
    create();

    split.setExpanded(false);
    pump();

    assertThat(split.expanded()).isFalse();
    assertThat(panel.isVisible()).isFalse();
    assertThat(main.isVisible()).isTrue();
    assertThat(toolbar.isVisible()).isFalse();
    assertThat(split.strip().isVisible()).isTrue();
    assertThat(split.strip().getItem(0).getToolTipText()).isEqualTo("Show Branches");
    assertThat(changes).containsExactly(false);
  }

  @Test
  void theStripButtonExpandsThePanelAgain() {
    create();
    split.setExpanded(false);
    pump();

    split.strip().getItem(0).notifyListeners(SWT.Selection, new org.eclipse.swt.widgets.Event());
    pump();

    assertThat(split.expanded()).isTrue();
    assertThat(panel.isVisible()).isTrue();
    assertThat(split.strip().isVisible()).isFalse();
    assertThat(changes).containsExactly(false, true);
  }

  @Test
  void theCollapsedStripIsAsWideAsTheToolbar() {
    create();
    int toolbarWidth = toolbar.getSize().x;

    split.setExpanded(false);
    pump();

    assertThat(split.strip().getParent().getSize().x).isEqualTo(toolbarWidth);
  }
}
