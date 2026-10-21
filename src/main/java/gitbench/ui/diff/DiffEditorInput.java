package gitbench.ui.diff;

import gitbench.diff.DiffRequest;
import gitbench.ui.GitbenchIcons;
import gitbench.ui.GitbenchIcons.Icon;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IPersistableElement;

import java.util.List;

/**
 * The input of a diff tab: a chain of requests and the shown one. Previous File and Next File move along the chain.
 * Two inputs are equal when they show the same request, so opening the same diff again activates the open tab.
 */
final class DiffEditorInput implements IEditorInput {
  private final List<DiffRequest> chain;
  private final int index;

  DiffEditorInput(List<DiffRequest> chain, int index) {
    this.chain = List.copyOf(chain);
    this.index = index;
  }

  List<DiffRequest> chain() {
    return chain;
  }

  int index() {
    return index;
  }

  DiffRequest request() {
    return chain.get(index);
  }

  DiffEditorInput withIndex(int newIndex) {
    return new DiffEditorInput(chain, newIndex);
  }

  /** The same chain with the shown request replaced, for example by HEAD on the left after a commit. */
  DiffEditorInput withRequest(DiffRequest request) {
    List<DiffRequest> changed = new java.util.ArrayList<>(chain);
    changed.set(index, request);
    return new DiffEditorInput(changed, index);
  }

  @Override
  public boolean exists() {
    return false;
  }

  @Override
  public ImageDescriptor getImageDescriptor() {
    return GitbenchIcons.descriptor(Icon.DIFF);
  }

  @Override
  public String getName() {
    return request().title();
  }

  @Override
  public IPersistableElement getPersistable() {
    return null;
  }

  @Override
  public String getToolTipText() {
    return request().repository().root().resolve(request().path()).toString();
  }

  @Override
  public <T> T getAdapter(Class<T> adapter) {
    return null;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof DiffEditorInput other && request().equals(other.request());
  }

  @Override
  public int hashCode() {
    return request().hashCode();
  }
}
