package gitbench.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GitbenchIconsTest {
  @Test
  void everyIconHasPngForBothThemes() {
    Path png = Path.of(System.getProperty("gitbench.projectDir"), "build", "generated", "icons", "png");
    for (GitbenchIcons.Icon icon : GitbenchIcons.Icon.values()) {
      for (String theme : new String[]{"light", "dark"}) {
        assertThat(png.resolve(theme).resolve(icon.fileName() + ".png")).as(icon + " " + theme).exists();
        assertThat(png.resolve(theme).resolve(icon.fileName() + "@2x.png")).as(icon + " " + theme + " @2x").exists();
      }
    }
  }
}
