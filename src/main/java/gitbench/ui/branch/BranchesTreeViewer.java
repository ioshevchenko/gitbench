package gitbench.ui.branch;

import gitbench.branch.AheadBehind;
import gitbench.branch.BranchTreeBuilder;
import gitbench.branch.BranchTreeNode;
import gitbench.branch.BranchTreeNode.Branch;
import gitbench.branch.BranchTreeNode.Folder;
import gitbench.branch.BranchTreeNode.Group;
import gitbench.branch.BranchTreeNode.Head;
import gitbench.branch.BranchTreeNode.Remote;
import gitbench.log.GitRef;
import gitbench.log.RefType;
import gitbench.ui.GitbenchIcons;
import gitbench.ui.GitbenchIcons.Icon;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.OwnerDrawLabelProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.TreeItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The tree of the Branches view, painted by hand like the IntelliJ branches tree: an icon per node kind, the name,
 * and incoming/outgoing arrows with commit counts right after a branch name.
 */
final class BranchesTreeViewer {
  private static final int ICON_SIZE = 16;
  private static final int GAP = 4;

  private final TreeViewer viewer;

  BranchesTreeViewer(Composite parent) {
    viewer = new TreeViewer(parent, SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL | SWT.FULL_SELECTION);
    viewer.setContentProvider(new ITreeContentProvider() {
      @Override
      public Object[] getElements(Object input) {
        return ((List<?>)input).toArray();
      }

      @Override
      public Object[] getChildren(Object parent) {
        return childrenOf((BranchTreeNode)parent).toArray();
      }

      @Override
      public Object getParent(Object element) {
        return null;
      }

      @Override
      public boolean hasChildren(Object element) {
        return !childrenOf((BranchTreeNode)element).isEmpty();
      }
    });
    viewer.setLabelProvider(new Painter());
    OwnerDrawLabelProvider.setUpOwnerDraw(viewer);
  }

  TreeViewer viewer() {
    return viewer;
  }

  /** Shows a new tree and keeps the expanded nodes and the selection, matched by their label paths. */
  void setNodes(List<BranchTreeNode> nodes) {
    Set<String> expanded = new java.util.HashSet<>();
    collectExpanded(viewer.getTree().getItems(), "", expanded);
    Set<String> selected = new java.util.HashSet<>();
    for (TreeItem item : viewer.getTree().getSelection()) {
      selected.add(pathOf(item));
    }
    boolean first = viewer.getInput() == null;
    viewer.getTree().setRedraw(false);
    try {
      viewer.setInput(nodes);
      if (first) {
        viewer.expandToLevel(3);
      }
      else {
        List<Object> toExpand = new ArrayList<>();
        List<Object> toSelect = new ArrayList<>();
        for (BranchTreeNode node : nodes) {
          walk(node, "", (n, path) -> {
            if (expanded.contains(path)) {
              toExpand.add(n);
            }
            if (selected.contains(path)) {
              toSelect.add(n);
            }
          });
        }
        viewer.setExpandedElements(toExpand.toArray());
        viewer.setSelection(new StructuredSelection(toSelect));
      }
    }
    finally {
      viewer.getTree().setRedraw(true);
    }
  }

  List<BranchTreeNode> selectedNodes() {
    List<BranchTreeNode> nodes = new ArrayList<>();
    for (Object element : ((IStructuredSelection)viewer.getSelection()).toList()) {
      nodes.add((BranchTreeNode)element);
    }
    return nodes;
  }

  /** Returns the refs under the selected nodes. HEAD counts as a ref. */
  static List<GitRef> refsUnder(List<BranchTreeNode> nodes, List<GitRef> allRefs) {
    List<GitRef> refs = new ArrayList<>();
    for (BranchTreeNode node : nodes) {
      if (node instanceof Head) {
        allRefs.stream().filter(r -> r.type() == RefType.HEAD).findFirst().ifPresent(refs::add);
      }
      else {
        walk(node, "", (n, path) -> {
          if (n instanceof Branch branch) {
            refs.add(branch.ref());
          }
        });
      }
    }
    return refs.stream().distinct().toList();
  }

  private static List<BranchTreeNode> childrenOf(BranchTreeNode node) {
    if (node instanceof Group group) {
      return group.children();
    }
    if (node instanceof Remote remote) {
      return remote.children();
    }
    if (node instanceof Folder folder) {
      return folder.children();
    }
    return List.of();
  }

  private static void walk(BranchTreeNode node, String parentPath, java.util.function.BiConsumer<BranchTreeNode, String> visitor) {
    String path = parentPath + "/" + BranchTreeBuilder.labelOf(node);
    visitor.accept(node, path);
    for (BranchTreeNode child : childrenOf(node)) {
      walk(child, path, visitor);
    }
  }

  private static void collectExpanded(TreeItem[] items, String parentPath, Set<String> out) {
    for (TreeItem item : items) {
      if (!(item.getData() instanceof BranchTreeNode node)) {
        continue;
      }
      String path = parentPath + "/" + BranchTreeBuilder.labelOf(node);
      if (item.getExpanded()) {
        out.add(path);
      }
      collectExpanded(item.getItems(), path, out);
    }
  }

  /** The label path of a tree item, in the same form as {@link #walk}. */
  private static String pathOf(TreeItem item) {
    StringBuilder path = new StringBuilder();
    for (TreeItem current = item; current != null; current = current.getParentItem()) {
      if (current.getData() instanceof BranchTreeNode node) {
        path.insert(0, "/" + BranchTreeBuilder.labelOf(node));
      }
    }
    return path.toString();
  }

  private static Image iconOf(BranchTreeNode node) {
    if (node instanceof Branch branch) {
      boolean current = branch.ref().current();
      if (current && branch.favorite()) {
        return GitbenchIcons.get(Icon.CURRENT_BRANCH_FAVORITE_LABEL);
      }
      if (current) {
        return GitbenchIcons.get(Icon.CURRENT_BRANCH_LABEL);
      }
      return GitbenchIcons.get(branch.favorite() ? Icon.STAR : Icon.VCS);
    }
    if (node instanceof Remote || node instanceof Folder) {
      return GitbenchIcons.get(Icon.FOLDER);
    }
    return null;
  }

  /** Paints icon, name, and counts for each row. */
  private static final class Painter extends OwnerDrawLabelProvider {
    @Override
    protected void measure(Event event, Object element) {
      BranchTreeNode node = (BranchTreeNode)element;
      Point text = event.gc.textExtent(BranchTreeBuilder.labelOf(node));
      event.width = ICON_SIZE + GAP + text.x + countsWidth(event.gc, node) + GAP;
      event.height = Math.max(event.height, Math.max(text.y, ICON_SIZE) + 4);
    }

    @Override
    protected void erase(Event event, Object element) {
      event.detail &= ~SWT.FOREGROUND;
    }

    @Override
    protected void paint(Event event, Object element) {
      BranchTreeNode node = (BranchTreeNode)element;
      GC gc = event.gc;
      Rectangle bounds = ((TreeItem)event.item).getBounds(event.index);
      int x = bounds.x;
      Image icon = iconOf(node);
      if (icon != null) {
        gc.drawImage(icon, x, bounds.y + (bounds.height - ICON_SIZE) / 2);
        x += ICON_SIZE + GAP;
      }
      boolean selected = (event.detail & SWT.SELECTED) != 0;
      Color foreground = selected ? gc.getDevice().getSystemColor(SWT.COLOR_LIST_SELECTION_TEXT)
                                  : ((TreeItem)event.item).getParent().getForeground();
      gc.setForeground(foreground);
      String label = BranchTreeBuilder.labelOf(node);
      Point extent = gc.textExtent(label);
      gc.drawText(label, x, bounds.y + (bounds.height - extent.y) / 2, true);
      x += extent.x + GAP;
      if (node instanceof Branch branch && branch.counts() != null) {
        paintCounts(gc, branch.counts(), x, bounds);
      }
    }

    private static int countsWidth(GC gc, BranchTreeNode node) {
      if (!(node instanceof Branch branch) || branch.counts() == null) {
        return 0;
      }
      AheadBehind.Counts counts = branch.counts();
      int width = 0;
      if (counts.incoming() > 0) {
        width += ICON_SIZE + gc.textExtent(String.valueOf(counts.incoming())).x + GAP;
      }
      if (counts.outgoing() > 0) {
        width += ICON_SIZE + gc.textExtent(String.valueOf(counts.outgoing())).x + GAP;
      }
      return width;
    }

    private static void paintCounts(GC gc, AheadBehind.Counts counts, int x, Rectangle bounds) {
      Color old = gc.getForeground();
      gc.setForeground(gc.getDevice().getSystemColor(SWT.COLOR_WIDGET_DISABLED_FOREGROUND));
      if (counts.incoming() > 0) {
        x = paintCount(gc, Icon.INCOMING, counts.incoming(), x, bounds);
      }
      if (counts.outgoing() > 0) {
        paintCount(gc, Icon.OUTGOING, counts.outgoing(), x, bounds);
      }
      gc.setForeground(old);
    }

    private static int paintCount(GC gc, Icon icon, int count, int x, Rectangle bounds) {
      gc.drawImage(GitbenchIcons.get(icon), x, bounds.y + (bounds.height - ICON_SIZE) / 2);
      x += ICON_SIZE;
      String text = String.valueOf(count);
      Point extent = gc.textExtent(text);
      gc.drawText(text, x, bounds.y + (bounds.height - extent.y) / 2, true);
      return x + extent.x + GAP;
    }
  }
}
