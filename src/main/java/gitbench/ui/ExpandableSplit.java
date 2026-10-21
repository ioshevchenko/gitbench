package gitbench.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.ToolBar;

import java.util.function.Consumer;

/**
 * A side panel that collapses to a thin strip, as the branches panel of the IntelliJ Git Log
 * ({@code ExpandablePanelController}). A column on the left holds the toolbar of the panel when it is expanded and
 * the strip with one expand button when it is collapsed. A sash right of the column splits the panel and the main
 * content.
 */
public final class ExpandableSplit {
  private final Composite controls;
  private final StackLayout controlsLayout = new StackLayout();
  private final SashForm sash;
  private final ToolBar strip;
  private final Consumer<Boolean> listener;
  private Control toolbar;
  private Control panel;
  private Control main;

  /** {@code listener} receives the new state when the user or {@link #setExpanded} changes it. */
  public ExpandableSplit(Composite parent, GitbenchIcons.Icon expandIcon, String expandTip, Consumer<Boolean> listener) {
    this.listener = listener;
    Composite root = new Composite(parent, SWT.NONE);
    GridLayout layout = new GridLayout(2, false);
    layout.marginWidth = 0;
    layout.marginHeight = 0;
    layout.horizontalSpacing = 0;
    root.setLayout(layout);
    controls = new Composite(root, SWT.NONE);
    controls.setLayout(controlsLayout);
    controls.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, true));
    sash = new SashForm(root, SWT.HORIZONTAL);
    sash.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    Separators.onePixel(sash);
    strip = new ToolBar(controls, SWT.VERTICAL | SWT.FLAT);
    org.eclipse.swt.widgets.ToolItem expand = new org.eclipse.swt.widgets.ToolItem(strip, SWT.PUSH);
    expand.setImage(GitbenchIcons.get(expandIcon));
    expand.setToolTipText(expandTip);
    expand.addListener(SWT.Selection, e -> setExpanded(true));
  }

  /** The parent for the toolbar of the panel. */
  public Composite controls() {
    return controls;
  }

  /** The parent for the panel and the main content, in this order. */
  public SashForm sash() {
    return sash;
  }

  ToolBar strip() {
    return strip;
  }

  /** Sets the children made in {@link #controls()} and {@link #sash()}; the panel takes {@code panelPercent} of the width. */
  public void setContents(Control toolbar, Control panel, Control main, int panelPercent) {
    this.toolbar = toolbar;
    this.panel = panel;
    this.main = main;
    sash.setWeights(panelPercent, 100 - panelPercent);
    show(true);
  }

  public boolean expanded() {
    return sash.getMaximizedControl() == null;
  }

  public void setExpanded(boolean expanded) {
    if (expanded == expanded()) {
      return;
    }
    show(expanded);
    listener.accept(expanded);
  }

  private void show(boolean expanded) {
    // The stack keeps the column as wide as its widest child, so the strip is as wide as the toolbar.
    controlsLayout.topControl = expanded ? toolbar : strip;
    toolbar.setVisible(expanded);
    strip.setVisible(!expanded);
    // A hidden panel also leaves the focus order; the sash alone only moves it out of sight.
    panel.setVisible(expanded);
    sash.setMaximizedControl(expanded ? null : main);
    controls.getParent().layout(true, true);
  }
}
