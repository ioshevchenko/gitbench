package gitbench.ui.diff;

import com.intellij.diff.comparison.DiffTooBigException;
import gitbench.diff.BlockApplier;
import gitbench.diff.DiffChange;
import gitbench.diff.DiffContentLoader;
import gitbench.diff.DiffModel;
import gitbench.diff.DiffRequest;
import gitbench.diff.DiffSide;
import gitbench.diff.DiffText;
import gitbench.diff.Edit;
import gitbench.diff.HighlightPolicy;
import gitbench.diff.IgnorePolicy;
import gitbench.diff.LoadedContent;
import gitbench.diff.Side;
import gitbench.git.GitException;
import gitbench.ui.Activator;
import gitbench.ui.Theme;
import org.eclipse.core.filebuffers.IFileBuffer;
import org.eclipse.core.filebuffers.IFileBufferListener;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.TextUtilities;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.part.EditorPart;

import java.nio.file.Path;
import java.util.List;

/**
 * The Gitbench diff tab, as the IntelliJ diff editor: a toolbar and a Side-by-side or Unified viewer. The right side
 * can be edited when it is the working file; it shares the file buffer with the C editor, so Save writes the file.
 */
public final class DiffEditor extends EditorPart implements DiffPane.Host, DiffToolbar.Actions {
  public static final String ID = "gitbench.editors.diff";
  /** The pause after typing before the diff is computed again, so typing does not compare on every key. */
  private static final int REDIFF_DELAY_MS = 300;

  private Composite root;
  private DiffToolbar toolbar;
  private Label banner;
  private Composite body;
  private StackLayout bodyLayout;
  private Label message;
  private DiffPane pane;
  private DiffContent content;
  private Path connectedFile;
  private ITextFileBuffer buffer;
  private int loadGeneration;
  private int diffGeneration;
  private int documentVersion;
  private int comparedVersion = -1;
  private boolean rediffScheduled;
  private final IDocumentListener documentListener = new IDocumentListener() {
    @Override
    public void documentAboutToBeChanged(DocumentEvent event) {
    }

    @Override
    public void documentChanged(DocumentEvent event) {
      documentVersion++;
      firePropertyChange(PROP_DIRTY);
      scheduleRediff();
    }
  };
  private final IFileBufferListener bufferListener = new DirtyListener();

  @Override
  public void init(IEditorSite site, IEditorInput input) throws PartInitException {
    if (!(input instanceof DiffEditorInput)) {
      throw new PartInitException("Gitbench Diff opens only diff inputs");
    }
    setSite(site);
    setInput(input);
    setPartName(input.getName());
    setTitleToolTip(input.getToolTipText());
  }

  private DiffEditorInput diffInput() {
    return (DiffEditorInput)getEditorInput();
  }

  @Override
  public void createPartControl(Composite parent) {
    root = new Composite(parent, SWT.NONE);
    GridLayout layout = new GridLayout(1, false);
    layout.marginWidth = 0;
    layout.marginHeight = 0;
    layout.verticalSpacing = 0;
    root.setLayout(layout);
    toolbar = new DiffToolbar(root, this);
    toolbar.control().setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    banner = new Label(root, SWT.NONE);
    GridData bannerData = new GridData(SWT.FILL, SWT.CENTER, true, false);
    bannerData.exclude = true;
    banner.setLayoutData(bannerData);
    banner.setVisible(false);
    body = new Composite(root, SWT.NONE);
    body.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    bodyLayout = new StackLayout();
    body.setLayout(bodyLayout);
    message = new Label(body, SWT.CENTER);
    installGlobalActions();
    load();
  }

  private void installGlobalActions() {
    IActionBars bars = getEditorSite().getActionBars();
    bars.setGlobalActionHandler(ActionFactory.UNDO.getId(), operation(ITextOperationTarget.UNDO));
    bars.setGlobalActionHandler(ActionFactory.REDO.getId(), operation(ITextOperationTarget.REDO));
    bars.setGlobalActionHandler(ActionFactory.CUT.getId(), operation(ITextOperationTarget.CUT));
    bars.setGlobalActionHandler(ActionFactory.COPY.getId(), operation(ITextOperationTarget.COPY));
    bars.setGlobalActionHandler(ActionFactory.PASTE.getId(), operation(ITextOperationTarget.PASTE));
    bars.setGlobalActionHandler(ActionFactory.SELECT_ALL.getId(), operation(ITextOperationTarget.SELECT_ALL));
    bars.updateActionBars();
  }

  private Action operation(int operation) {
    return new Action() {
      @Override
      public void run() {
        ITextOperationTarget target = pane != null ? pane.focusedTarget() : null;
        if (target != null && target.canDoOperation(operation)) {
          target.doOperation(operation);
        }
      }
    };
  }

  // Loading

  private void load() {
    int generation = ++loadGeneration;
    DiffRequest request = diffInput().request();
    showMessage("Loading...");
    toolbar.setCount(0, "");
    DiffEditorInput input = diffInput();
    toolbar.setFileNavigation(input.chain().size() > 1, input.index() > 0, input.index() < input.chain().size() - 1);
    var charset = SideDocuments.charsetOf(request.repository().root().resolve(request.path()));
    Job job = Job.create("Load diff of " + request.title(), monitor -> {
      try {
        LoadedContent leftContent = DiffContentLoader.load(Activator.getDefault().gitRunner(), request.repository(), request.left(), charset);
        LoadedContent rightContent = DiffContentLoader.load(Activator.getDefault().gitRunner(), request.repository(), request.right(), charset);
        asyncExec(() -> {
          if (generation == loadGeneration) {
            loaded(request, leftContent, rightContent);
          }
        });
      }
      catch (GitException e) {
        asyncExec(() -> {
          if (generation == loadGeneration) {
            showMessage(e.userMessage());
          }
        });
      }
      return Status.OK_STATUS;
    });
    job.setSystem(true);
    job.schedule();
  }

  private void loaded(DiffRequest request, LoadedContent leftContent, LoadedContent rightContent) {
    if (leftContent.binary() || rightContent.binary()) {
      showMessage("Files contents are different");
      return;
    }
    IDocument leftDocument = SideDocuments.revision(leftContent);
    IDocument rightDocument;
    if (request.right() instanceof DiffSide.WorkingFile file) {
      try {
        buffer = SideDocuments.connect(file.absolutePath());
        connectedFile = file.absolutePath();
      }
      catch (CoreException e) {
        showMessage(e.getMessage());
        return;
      }
      rightDocument = buffer.getDocument();
      rightDocument.addDocumentListener(documentListener);
      SideDocuments.manager().addFileBufferListener(bufferListener);
    }
    else {
      rightDocument = SideDocuments.revision(rightContent);
    }
    boolean localChanges = request.left() instanceof DiffSide.Revision && request.right() instanceof DiffSide.WorkingFile;
    content = new DiffContent(leftDocument, rightDocument, leftContent.title(), rightContent.title(), request.right().editable(),
                              localChanges, request.repository().root().resolve(request.path()), DiffText.of(""), DiffText.of(""), List.of());
    createPane();
    firePropertyChange(PROP_DIRTY);
    rediff();
  }

  private void createPane() {
    if (pane != null) {
      pane.dispose();
      pane = null;
    }
    pane = createPane(DiffSettings.viewer());
    bodyLayout.topControl = pane.control();
    body.layout(true);
  }

  private DiffPane createPane(DiffSettings.Viewer viewer) {
    return viewer == DiffSettings.Viewer.UNIFIED ? new UnifiedViewer(body, this, content, Theme.isDark())
                                                 : new SplitViewer(body, this, content, Theme.isDark());
  }

  private void unload() {
    loadGeneration++;
    diffGeneration++;
    if (content != null && buffer != null) {
      content.right().removeDocumentListener(documentListener);
      SideDocuments.manager().removeFileBufferListener(bufferListener);
    }
    if (connectedFile != null) {
      SideDocuments.disconnect(connectedFile);
    }
    buffer = null;
    connectedFile = null;
    content = null;
    if (pane != null) {
      pane.dispose();
      pane = null;
    }
  }

  private void showMessage(String text) {
    message.setText(text);
    bodyLayout.topControl = message;
    body.layout(true);
    setBanner(null);
  }

  private void setBanner(String text) {
    boolean visible = text != null;
    banner.setText(visible ? " " + text : "");
    banner.setVisible(visible);
    ((GridData)banner.getLayoutData()).exclude = !visible;
    root.layout(true);
  }

  // Comparison

  private void scheduleRediff() {
    if (rediffScheduled) {
      return;
    }
    rediffScheduled = true;
    root.getDisplay().timerExec(REDIFF_DELAY_MS, () -> {
      rediffScheduled = false;
      if (!root.isDisposed()) {
        rediff();
      }
    });
  }

  private void rediff() {
    if (content == null) {
      return;
    }
    int generation = ++diffGeneration;
    int version = documentVersion;
    String leftRaw = content.left().get();
    String rightRaw = content.right().get();
    IgnorePolicy ignore = DiffSettings.ignore();
    HighlightPolicy highlight = DiffSettings.highlight();
    Job job = Job.create("Compare " + diffInput().request().title(), monitor -> {
      DiffText leftText = DiffText.of(leftRaw);
      DiffText rightText = DiffText.of(rightRaw);
      List<DiffChange> changes;
      String status = null;
      try {
        changes = DiffModel.compute(leftText, rightText, ignore, highlight);
      }
      catch (DiffTooBigException e) {
        changes = List.of();
        status = "Files are too large to compare";
      }
      String bannerText = status != null ? status : bannerText(leftRaw, rightRaw, leftText, rightText, changes, highlight);
      List<DiffChange> result = changes;
      asyncExec(() -> {
        if (generation == diffGeneration && content != null && pane != null) {
          content = content.withChanges(leftText, rightText, result);
          comparedVersion = version;
          pane.setContent(content);
          toolbar.setCount(result.size(), null);
          setBanner(bannerText);
        }
      });
      return Status.OK_STATUS;
    });
    job.setSystem(true);
    job.schedule();
  }

  /** The notice above the columns, as the IntelliJ diff notifications. */
  private static String bannerText(String leftRaw, String rightRaw, DiffText left, DiffText right, List<DiffChange> changes,
                                   HighlightPolicy highlight) {
    if (highlight == HighlightPolicy.DO_NOT_HIGHLIGHT) {
      return "Differences highlighting is off";
    }
    if (!changes.isEmpty()) {
      return null;
    }
    if (leftRaw.equals(rightRaw)) {
      return "Contents are identical";
    }
    if (left.text().equals(right.text())) {
      return "Contents have differences only in line separators";
    }
    return "Differences ignored";
  }

  // DiffPane.Host

  @Override
  public void apply(DiffChange change, boolean append) {
    if (content == null || !content.rightEditable()) {
      return;
    }
    if (comparedVersion != documentVersion) {
      // The blocks are older than the text: compare first, then the user sees the current blocks.
      rediff();
      return;
    }
    Edit edit = append ? BlockApplier.append(content.leftText(), content.rightText(), change, Side.LEFT)
                       : BlockApplier.replace(content.leftText(), content.rightText(), change, Side.LEFT);
    if (edit == null) {
      return;
    }
    IDocument document = content.right();
    try {
      int start = document.getLineOffset(edit.startLine()) + edit.startColumn();
      int end = document.getLineOffset(edit.endLine()) + edit.endColumn();
      String delimiter = TextUtilities.getDefaultLineDelimiter(document);
      document.replace(start, end - start, edit.text().replace("\n", delimiter));
    }
    catch (BadLocationException e) {
      Activator.getDefault().getLog().error("Cannot apply the diff block", e);
    }
    rediff();
  }

  @Override
  public boolean handleKey(Event event) {
    int modifiers = event.stateMask & SWT.MODIFIER_MASK;
    if (event.keyCode == SWT.F7 && modifiers == 0) {
      nextDifference();
      return true;
    }
    if (event.keyCode == SWT.F7 && modifiers == SWT.SHIFT) {
      previousDifference();
      return true;
    }
    if (event.keyCode == SWT.ARROW_RIGHT && modifiers == (SWT.ALT | SWT.SHIFT)) {
      nextFile();
      return true;
    }
    if (event.keyCode == SWT.ARROW_LEFT && modifiers == (SWT.ALT | SWT.SHIFT)) {
      previousFile();
      return true;
    }
    if ((event.keyCode == 'r' || event.keyCode == 'R') && modifiers == (SWT.CTRL | SWT.ALT)) {
      DiffChange change = pane != null ? pane.changeAtCaret() : null;
      if (change != null) {
        apply(change, false);
      }
      return true;
    }
    return false;
  }

  @Override
  public void contentChanged() {
    scheduleRediff();
  }

  // DiffToolbar.Actions

  @Override
  public void previousDifference() {
    if (pane != null) {
      pane.navigate(false);
    }
  }

  @Override
  public void nextDifference() {
    if (pane != null) {
      pane.navigate(true);
    }
  }

  @Override
  public void previousFile() {
    showFile(diffInput().index() - 1);
  }

  @Override
  public void nextFile() {
    showFile(diffInput().index() + 1);
  }

  private void showFile(int index) {
    DiffEditorInput input = diffInput();
    if (index < 0 || index >= input.chain().size() || isDirty() && !saveBeforeLeaving()) {
      return;
    }
    unload();
    DiffEditorInput next = input.withIndex(index);
    setInputWithNotify(next);
    setPartName(next.getName());
    setTitleToolTip(next.getToolTipText());
    load();
  }

  /**
   * Saves the working file before the tab shows another file. Without a C editor on the file, closing the buffer
   * would drop the edits; IntelliJ keeps them because its documents live on, so the edits must reach the disk here.
   */
  private boolean saveBeforeLeaving() {
    doSave(null);
    return !isDirty();
  }

  @Override
  public void viewerChanged(DiffSettings.Viewer viewer) {
    DiffSettings.setViewer(viewer);
    if (content != null) {
      // Keep the block under the caret in view, as IntelliJ does when the viewer changes.
      DiffChange current = pane != null ? pane.changeAtCaret() : null;
      createPane();
      if (current != null) {
        pane.reveal(current);
      }
    }
  }

  @Override
  public void ignoreChanged(IgnorePolicy policy) {
    DiffSettings.setIgnore(policy);
    rediff();
  }

  @Override
  public void highlightChanged(HighlightPolicy policy) {
    DiffSettings.setHighlight(policy);
    rediff();
  }

  // EditorPart

  @Override
  public void doSave(IProgressMonitor monitor) {
    if (buffer == null) {
      return;
    }
    try {
      buffer.commit(monitor, true);
    }
    catch (CoreException e) {
      ErrorDialog.openError(getSite().getShell(), "Save", "Cannot save " + connectedFile, e.getStatus());
    }
    firePropertyChange(PROP_DIRTY);
  }

  @Override
  public void doSaveAs() {
  }

  @Override
  public boolean isDirty() {
    return buffer != null && buffer.isDirty();
  }

  @Override
  public boolean isSaveAsAllowed() {
    return false;
  }

  @Override
  public void setFocus() {
    if (pane != null) {
      pane.setFocus();
    }
    else if (root != null) {
      root.setFocus();
    }
  }

  @Override
  public void dispose() {
    unload();
    super.dispose();
  }

  private void asyncExec(Runnable runnable) {
    if (root == null || root.isDisposed()) {
      return;
    }
    root.getDisplay().asyncExec(() -> {
      if (!root.isDisposed()) {
        runnable.run();
      }
    });
  }

  /** Updates the dirty mark when the C editor saves or reverts the shared buffer. */
  private final class DirtyListener implements IFileBufferListener {
    @Override
    public void dirtyStateChanged(IFileBuffer changed, boolean isDirty) {
      if (changed == buffer) {
        asyncExec(() -> firePropertyChange(PROP_DIRTY));
      }
    }

    @Override
    public void bufferCreated(IFileBuffer created) {
    }

    @Override
    public void bufferDisposed(IFileBuffer disposed) {
    }

    @Override
    public void bufferContentAboutToBeReplaced(IFileBuffer changed) {
    }

    @Override
    public void bufferContentReplaced(IFileBuffer changed) {
    }

    @Override
    public void stateChanging(IFileBuffer changed) {
    }

    @Override
    public void stateValidationChanged(IFileBuffer changed, boolean isStateValidated) {
    }

    @Override
    public void underlyingFileMoved(IFileBuffer changed, IPath path) {
    }

    @Override
    public void underlyingFileDeleted(IFileBuffer changed) {
    }

    @Override
    public void stateChangeFailed(IFileBuffer changed) {
    }
  }
}
