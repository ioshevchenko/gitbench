package gitbench.ui.diff;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The plugin calls CDT through reflection, so the compiler does not check those calls. This test finds every method
 * and constructor of {@link CdtSupport.CdtApi} in the real CDT 11.6.0 jars, the CDT of Eclipse 4.33.
 */
class CdtApiTest {
  @Test
  void cdtHasEveryCallOfTheHighlighting() throws Exception {
    URL[] jars = Arrays.stream(System.getProperty("gitbench.cdtJars").split(File.pathSeparator))
      .map(path -> {
        try {
          return new File(path).toURI().toURL();
        }
        catch (java.net.MalformedURLException e) {
          throw new IllegalStateException(e);
        }
      })
      .toArray(URL[]::new);
    try (URLClassLoader cdt = new URLClassLoader(jars, CdtApiTest.class.getClassLoader())) {
      CdtSupport.CdtApi api = CdtSupport.CdtApi.resolve(cdt);

      assertThat(api.cConfiguration().getDeclaringClass().getName()).isEqualTo("org.eclipse.cdt.ui.text.CSourceViewerConfiguration");
      assertThat(api.asmConfiguration().getDeclaringClass().getName()).isEqualTo("org.eclipse.cdt.ui.text.AsmSourceViewerConfiguration");
      assertThat(api.setupCDocument()).isNotNull();
      assertThat(api.setupAsmDocument()).isNotNull();
      assertThat(api.getCombinedPreferenceStore()).isNotNull();
    }
  }
}
