package gitbench.build;

import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Provider;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Copies a jar without its signature files. Eclipse splits some packages between signed jars, for example
 * org.eclipse.jface.text.source between org.eclipse.text and org.eclipse.jface.text. OSGi loads them per bundle, but
 * a plain test class path rejects a package with two signers, so the UI tests run on unsigned copies.
 */
public abstract class UnsignJar implements TransformAction<TransformParameters.None> {
  @InputArtifact
  public abstract Provider<FileSystemLocation> getInputArtifact();

  @Override
  public void transform(TransformOutputs outputs) {
    File input = getInputArtifact().get().getAsFile();
    if (!input.getName().endsWith(".jar")) {
      outputs.file(input);
      return;
    }
    File output = outputs.file(input.getName().replace(".jar", "-unsigned.jar"));
    try (ZipFile zip = new ZipFile(input); ZipOutputStream out = new ZipOutputStream(new java.io.FileOutputStream(output))) {
      Enumeration<? extends ZipEntry> entries = zip.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        if (isSignature(entry.getName())) {
          continue;
        }
        out.putNextEntry(new ZipEntry(entry.getName()));
        try (InputStream in = zip.getInputStream(entry)) {
          in.transferTo(out);
        }
        out.closeEntry();
      }
    }
    catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static boolean isSignature(String name) {
    String upper = name.toUpperCase(Locale.ROOT);
    return upper.startsWith("META-INF/") && upper.indexOf('/', "META-INF/".length()) < 0
           && (upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA") || upper.endsWith(".EC"));
  }
}
