package eu.bbmri_eric.negotiator.lifecycle.graph;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Structural ratchet keeping graph values independent of persistence, Spring, and evaluation. */
class CompiledGraphPurityTest {

  @Test
  void graphPackageDependsOnlyOnTheJdk() {
    List<Path> sources = sources();
    assertFalse(sources.isEmpty(), "The purity check must not pass over an empty source tree");

    for (Path source : sources) {
      for (String line : readLines(source)) {
        String trimmed = line.trim();
        if (trimmed.startsWith("import ")) {
          assertTrue(
              trimmed.startsWith("import java."),
              () -> source + " imports a non-JDK graph dependency: " + trimmed);
        }
      }
    }
  }

  private static List<Path> sources() {
    Path root = Path.of("src/main/java/eu/bbmri_eric/negotiator/lifecycle/graph").toAbsolutePath();
    try (var paths = Files.walk(root)) {
      return paths.filter(path -> path.toString().endsWith(".java")).toList();
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  private static List<String> readLines(Path path) {
    try {
      return Files.readAllLines(path);
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }
}
