// Gitbench shim: a minimal replacement for the IntelliJ Platform class with the same name (Apache 2.0).
package com.intellij.openapi.util.io;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class FileUtil {
  private FileUtil() {
  }

  /** Reads the file as UTF-8. With convertLineSeparators, CR LF and CR become LF. */
  public static String loadFile(File file, boolean convertLineSeparators) throws IOException {
    String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
    return convertLineSeparators ? text.replace("\r\n", "\n").replace('\r', '\n') : text;
  }
}
