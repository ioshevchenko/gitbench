package gitbench.ui;

import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

/** The plugin activator. It owns the shared services of the Gitbench bundle. */
public final class Activator extends AbstractUIPlugin {
  public static final String PLUGIN_ID = "gitbench";

  private static Activator plugin;

  @Override
  public void start(BundleContext context) throws Exception {
    super.start(context);
    plugin = this;
  }

  @Override
  public void stop(BundleContext context) throws Exception {
    plugin = null;
    super.stop(context);
  }

  public static Activator getDefault() {
    return plugin;
  }
}
