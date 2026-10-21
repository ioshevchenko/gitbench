package gitbench;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Manifest;

import static org.assertj.core.api.Assertions.assertThat;

class BuildSmokeTest {
  static Path projectDir() {
    return Path.of(System.getProperty("gitbench.projectDir"));
  }

  @Test
  void manifestHasBundleName() throws Exception {
    try (InputStream in = Files.newInputStream(projectDir().resolve("META-INF/MANIFEST.MF"))) {
      Manifest manifest = new Manifest(in);
      assertThat(manifest.getMainAttributes().getValue("Bundle-SymbolicName")).startsWith("gitbench");
    }
  }
}
