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

  private String gitPath() {
    String path = getPreferenceStore().getString(PREF_GIT_PATH);
    return path.isBlank() ? "git" : path;
  }
}
