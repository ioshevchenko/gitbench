package gitbench.ui.commit;

import gitbench.git.FileChange;
import gitbench.model.ChangesTreeBuilder;
import gitbench.model.ChangesTreeNode;
import gitbench.model.ChangesTreeNode.Dir;
import gitbench.model.ChangesTreeNode.File;
import gitbench.model.ChangesTreeNode.Root;
import gitbench.model.InclusionModel;
import gitbench.ui.GitbenchIcons;
import gitbench.ui.GitbenchIcons.Icon;
import gitbench.ui.StatusColors;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.jface.viewers.CheckboxTreeViewer;
import org.eclipse.jface.viewers.ICheckStateProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.StyledCellLabelProvider;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.TextStyle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.PlatformUI;

import java.util.ArrayList;
import java.util.List;

/** The tree of the Commit view: "Changes" and "Unversioned Files" with check boxes, as in IntelliJ. */
final class ChangesTreeViewer {
  private final CheckboxTreeViewer viewer;
  private final InclusionModel inclusion;
  private final LocalResourceManager resources;
  private final Runnable onInclusionChanged;
  private boolean groupByDirectory = true;
  private List<FileChange> changes = List.of();

  ChangesTreeViewer(Composite parent, InclusionModel inclusion, Runnable onInclusionChanged) {
    this.inclusion = inclusion;
    this.onInclusionChanged = onInclusionChanged;
    viewer = new CheckboxTreeViewer(parent, SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL | SWT.FULL_SELECTION);
    resources = new LocalResourceManager(JFaceResources.getResources(), viewer.getControl());
    viewer.setUseHashlookup(false);
    viewer.setContentProvider(new ContentProvider());
    viewer.setLabelProvider(new LabelProvider());
    viewer.setCheckStateProvider(new ICheckStateProvider() {
      @Override
      public boolean isChecked(Object element) {
        return inclusion.stateOf((ChangesTreeNode)element) != InclusionModel.State.OFF;
      }

      @Override
      public boolean isGrayed(Object element) {
        return inclusion.stateOf((ChangesTreeNode)element) == InclusionModel.State.PARTIAL;
      }
    });
    viewer.addCheckStateListener(event -> {
      List<FileChange> files = new ArrayList<>();
      InclusionModel.collect((ChangesTreeNode)event.getElement(), files);
      inclusion.setIncluded(files, event.getChecked());
      viewer.refresh();
      onInclusionChanged.run();
    });
  }

  CheckboxTreeViewer viewer() {
    return viewer;
  }

  boolean groupByDirectory() {
    return groupByDirectory;
  }

  void setGroupByDirectory(boolean group) {
    groupByDirectory = group;
    setChanges(changes);
  }

  /** Shows a new status. Keeps the expanded folders and the selected files. */
  void setChanges(List<FileChange> newChanges) {
    changes = List.copyOf(newChanges);
    inclusion.retainOnly(changes);
    List<String> expandedPaths = pathsOf(viewer.getExpandedElements());
    List<String> selectedPaths = pathsOf(viewer.getStructuredSelection().toArray());
    org.eclipse.swt.widgets.TreeItem top = viewer.getTree().getTopItem();
    String topKey = top == null || top.getData() == null ? null : keyOf((ChangesTreeNode)top.getData());
    boolean first = viewer.getInput() == null;
    viewer.getTree().setRedraw(false);
    try {
      viewer.setInput(ChangesTreeBuilder.build(changes, groupByDirectory));
      if (first) {
        viewer.expandToLevel(2);
      }
      else {
        restore(expandedPaths, selectedPaths);
      }
      // The roots are always open, as in IntelliJ: new changes never hide under a collapsed "Changes".
      for (Object root : (List<?>)viewer.getInput()) {
        viewer.setExpandedState(root, true);
      }
      restoreTop(topKey);
    }
    finally {
      viewer.getTree().setRedraw(true);
    }
    onInclusionChanged.run();
  }

  /** Returns all files in the order of the tree: the chain of Previous File and Next File in the diff. */
  List<FileChange> changesInTreeOrder() {
    List<FileChange> files = new ArrayList<>();
    if (viewer.getInput() instanceof List<?> roots) {
      for (Object root : roots) {
        InclusionModel.collect((ChangesTreeNode)root, files);
      }
    }
    return files.stream().distinct().toList();
  }

  /** Tells whether only file nodes are selected, so a double click opens a diff and does not expand a folder. */
  boolean onlyFilesSelected() {
    List<?> selected = ((IStructuredSelection)viewer.getSelection()).toList();
    return !selected.isEmpty() && selected.stream().allMatch(ChangesTreeNode.File.class::isInstance);
  }

  /** Returns the files under the selected nodes. */
  List<FileChange> selectedChanges() {
    List<FileChange> files = new ArrayList<>();
    for (Object element : ((IStructuredSelection)viewer.getSelection()).toList()) {
      InclusionModel.collect((ChangesTreeNode)element, files);
    }
    return files.stream().distinct().toList();
  }

  private void restore(List<String> expandedPaths, List<String> selectedPaths) {
    List<Object> expand = new ArrayList<>();
    List<Object> select = new ArrayList<>();
    for (Object root : (List<?>)viewer.getInput()) {
      walk((ChangesTreeNode)root, node -> {
        String key = keyOf(node);
        if (expandedPaths.contains(key)) {
          expand.add(node);
        }
        if (selectedPaths.contains(key)) {
          select.add(node);
        }
      });
    }
    viewer.setExpandedElements(expand.toArray());
    viewer.setSelection(new org.eclipse.jface.viewers.StructuredSelection(select));
  }

  /** Scrolls back to the node that was at the top before the refresh. */
  private void restoreTop(String topKey) {
    if (topKey == null) {
      return;
    }
    for (org.eclipse.swt.widgets.TreeItem item : visibleItems(viewer.getTree().getItems())) {
      if (item.getData() != null && topKey.equals(keyOf((ChangesTreeNode)item.getData()))) {
        viewer.getTree().setTopItem(item);
        return;
      }
    }
  }

  private static List<org.eclipse.swt.widgets.TreeItem> visibleItems(org.eclipse.swt.widgets.TreeItem[] items) {
    List<org.eclipse.swt.widgets.TreeItem> all = new ArrayList<>();
    for (org.eclipse.swt.widgets.TreeItem item : items) {
      all.add(item);
      if (item.getExpanded()) {
        all.addAll(visibleItems(item.getItems()));
      }
    }
    return all;
  }

  private static void walk(ChangesTreeNode node, java.util.function.Consumer<ChangesTreeNode> visitor) {
    visitor.accept(node);
    for (ChangesTreeNode child : children(node)) {
      walk(child, visitor);
    }
  }

  private static List<String> pathsOf(Object[] nodes) {
    List<String> keys = new ArrayList<>();
    for (Object node : nodes) {
      keys.add(keyOf((ChangesTreeNode)node));
    }
    return keys;
  }

  /** A stable key of a node across refreshes. */
  private static String keyOf(ChangesTreeNode node) {
    if (node instanceof Root root) {
      return "root:" + root.title();
    }
    if (node instanceof Dir dir) {
      return "dir:" + dir.path();
    }
    return "file:" + ((File)node).change().path();
  }

  private static List<ChangesTreeNode> children(ChangesTreeNode node) {
    if (node instanceof Root root) {
      return root.children();
    }
    if (node instanceof Dir dir) {
      return dir.children();
    }
    return List.of();
  }

  private static final class ContentProvider implements ITreeContentProvider {
    @Override
    public Object[] getElements(Object input) {
      return ((List<?>)input).toArray();
    }

    @Override
    public Object[] getChildren(Object parent) {
      return children((ChangesTreeNode)parent).toArray();
    }

    @Override
    public Object getParent(Object element) {
      return null;
    }

    @Override
    public boolean hasChildren(Object element) {
      return !children((ChangesTreeNode)element).isEmpty();
    }
  }

  private final class LabelProvider extends StyledCellLabelProvider {
    @Override
    public void update(ViewerCell cell) {
      ChangesTreeNode node = (ChangesTreeNode)cell.getElement();
      StyledString text = new StyledString();
      Image image = null;
      if (node instanceof Root root) {
        text.append(root.title());
        if (root.fileCount() > 0) {
          text.append("  " + root.fileCount() + (root.fileCount() == 1 ? " file" : " files"), StyledString.DECORATIONS_STYLER);
        }
      }
      else if (node instanceof Dir dir) {
        text.append(dir.label());
        image = GitbenchIcons.get(Icon.FOLDER);
      }
      else if (node instanceof File file) {
        FileChange change = file.change();
        text.append(change.fileName(), new StyledString.Styler() {
          @Override
          public void applyStyles(TextStyle style) {
            style.foreground = StatusColors.foreground(change.kind());
          }
        });
        if (!groupByDirectory && !change.parentPath().isEmpty()) {
          text.append("  " + change.parentPath(), StyledString.QUALIFIER_STYLER);
        }
        image = fileImage(change.fileName());
      }
      cell.setText(text.getString());
      cell.setStyleRanges(text.getStyleRanges());
      cell.setImage(image);
      super.update(cell);
    }

    private Image fileImage(String fileName) {
      ImageDescriptor descriptor = PlatformUI.getWorkbench().getEditorRegistry().getImageDescriptor(fileName);
      return descriptor == null ? null : resources.create(descriptor);
    }
  }
}
