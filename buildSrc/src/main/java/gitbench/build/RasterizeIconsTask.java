package gitbench.build;

import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;

/**
 * Turns {@code name.svg} and {@code name_dark.svg} into {@code light/name.png}, {@code light/name@2x.png},
 * {@code dark/name.png} and {@code dark/name@2x.png}. A missing dark variant falls back to the light one.
 * A name with the IntelliJ size suffix {@code @14x14} is rasterized at that size, and the suffix is dropped.
 */
public abstract class RasterizeIconsTask extends DefaultTask {
  @InputDirectory
  public abstract DirectoryProperty getSvgDir();

  @OutputDirectory
  public abstract DirectoryProperty getPngDir();

  @TaskAction
  public void rasterize() throws IOException {
    File svgDir = getSvgDir().get().getAsFile();
    File pngDir = getPngDir().get().getAsFile();
    File[] svgs = svgDir.listFiles((dir, name) -> name.endsWith(".svg") && !name.endsWith("_dark.svg"));
    if (svgs == null) {
      return;
    }
    for (File light : svgs) {
      String baseName = light.getName().substring(0, light.getName().length() - ".svg".length());
      File dark = new File(svgDir, baseName + "_dark.svg");
      int size = 16;
      String name = baseName;
      int at = baseName.indexOf('@');
      if (at >= 0) {
        size = Integer.parseInt(baseName.substring(at + 1, baseName.indexOf('x', at)));
        name = baseName.substring(0, at);
      }
      writePair(light, new File(pngDir, "light"), name, size);
      writePair(dark.exists() ? dark : light, new File(pngDir, "dark"), name, size);
    }
  }

  private static void writePair(File svg, File outDir, String name, int size) throws IOException {
    Files.createDirectories(outDir.toPath());
    write(svg, new File(outDir, name + ".png"), size);
    write(svg, new File(outDir, name + "@2x.png"), size * 2);
  }

  private static void write(File svg, File png, int size) throws IOException {
    PNGTranscoder transcoder = new PNGTranscoder();
    transcoder.addTranscodingHint(PNGTranscoder.KEY_WIDTH, (float)size);
    transcoder.addTranscodingHint(PNGTranscoder.KEY_HEIGHT, (float)size);
    try (OutputStream out = Files.newOutputStream(png.toPath())) {
      transcoder.transcode(new TranscoderInput(svg.toURI().toString()), new TranscoderOutput(out));
    }
    catch (TranscoderException e) {
      throw new GradleException("Cannot rasterize " + svg, e);
    }
  }
}
