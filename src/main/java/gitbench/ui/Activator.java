package gitbench.ui;

import gitbench.git.GitRunner;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

/** The plugin activator. It owns the shared services of the Gitbench bundle. */
public final class Activator extends AbstractUIPlugin {
  public static final String PLUGIN_ID = "gitbench";
  public static final String PREF_GIT_PATH = "gitPath";

  private static Activator plugin;

  private GitRunner gitRunner;

  @Override
  public void start(BundleContext context) throws Exception {
    super.start(context);
    plugin = this;
    getPreferenceStore().setDefault(PREF_GIT_PATH, "git");
    gitRunner = new GitRunner(this::gitPath, new GitConsole());
  }

  @Override
  public void stop(BundleContext context) throws Exception {
    plugin = null;
    super.stop(context);
  }

  public static Activator getDefault() {
    return plugin;
  }

  public GitRunner gitRunner() {
    return gitRunner;
  }

  /**
   * Git for the probes of the editor columns: they run on each activation and each pause in typing, so they do not
   * write to the Gitbench console. Without the running plugin, as in the UI tests, it is git from PATH.
   */
  public static GitRunner probeRunner() {
    Activator activator = getDefault();
    return activator == null ? new GitRunner(() -> "git", gitbench.git.GitCommandListener.NONE) : activator.gitRunner.quiet();
  }

  private String gitPath() {
    String path = getPreferenceStore().getString(PREF_GIT_PATH);
    return path.isBlank() ? "git" : path;
  }
}
