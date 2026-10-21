package gitbench.ui.log;

import gitbench.git.FileChange;
import gitbench.model.ChangesTreeBuilder;
import gitbench.model.ChangesTreeNode;
import gitbench.model.ChangesTreeNode.Dir;
import gitbench.model.ChangesTreeNode.File;
import gitbench.ui.GitbenchIcons;
import gitbench.ui.GitbenchIcons.Icon;
import gitbench.ui.StatusColors;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StyledCellLabelProvider;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.TextStyle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.PlatformUI;

import java.util.ArrayList;
import java.util.List;

/** The files of the selected commit, grouped by folder, in the status colors of the Commit view. */
final class CommitFilesTree {
  private final TreeViewer viewer;
  private final LocalResourceManager resources;

  CommitFilesTree(Composite parent) {
    viewer = new TreeViewer(parent, SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL | SWT.FULL_SELECTION);
    resources = new LocalResourceManager(JFaceResources.getResources(), viewer.getControl());
    viewer.setContentProvider(new ITreeContentProvider() {
      @Override
      public Object[] getElements(Object input) {
        return ((List<?>)input).toArray();
      }

      @Override
      public Object[] getChildren(Object parent) {
        return parent instanceof Dir dir ? dir.children().toArray() : new Object[0];
      }

      @Override
      public Object getParent(Object element) {
        return null;
      }

      @Override
      public boolean hasChildren(Object element) {
        return element instanceof Dir dir && !dir.children().isEmpty();
      }
    });
    viewer.setLabelProvider(new StyledCellLabelProvider() {
      @Override
      public void update(ViewerCell cell) {
        StyledString text = new StyledString();
        if (cell.getElement() instanceof Dir dir) {
          text.append(dir.label());
          cell.setImage(GitbenchIcons.get(Icon.FOLDER));
        }
        else if (cell.getElement() instanceof File file) {
          FileChange change = file.change();
          text.append(change.fileName(), new StyledString.Styler() {
            @Override
            public void applyStyles(TextStyle style) {
              style.foreground = StatusColors.foreground(change.kind());
            }
          });
          ImageDescriptor descriptor = PlatformUI.getWorkbench().getEditorRegistry().getImageDescriptor(change.fileName());
          cell.setImage(descriptor == null ? null : resources.create(descriptor));
        }
        cell.setText(text.getString());
        cell.setStyleRanges(text.getStyleRanges());
        super.update(cell);
      }
    });
  }

  TreeViewer viewer() {
    return viewer;
  }

  void setFiles(List<FileChange> files) {
    List<ChangesTreeNode> nodes = new ArrayList<>();
    ChangesTreeBuilder.build(files, true).forEach(root -> nodes.addAll(root.children()));
    viewer.setInput(nodes);
    viewer.expandAll();
  }

  List<FileChange> selectedFiles() {
    List<FileChange> result = new ArrayList<>();
    for (Object element : ((IStructuredSelection)viewer.getSelection()).toList()) {
      gitbench.model.InclusionModel.collect((ChangesTreeNode)element, result);
    }
    return result;
  }
}
