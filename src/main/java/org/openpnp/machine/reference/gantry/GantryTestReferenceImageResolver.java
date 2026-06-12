package org.openpnp.machine.reference.gantry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class GantryTestReferenceImageResolver {
    private GantryTestReferenceImageResolver() {
    }

    public static Path findReferenceBitmap(GantryTestCsvInput input, GantryTestPoint point) {
        Path csvSourceFile = input == null ? null : input.getSourceFile();

        return findReferenceBitmap(point.getReferenceBitmap(), point, csvSourceFile);
    }

    public static Path findReferenceBitmap(String referenceBitmap, GantryTestPoint point, Path csvSourceFile) {
        if (referenceBitmap == null || referenceBitmap.trim().isEmpty()) {
            return null;
        }

        String trimmed = referenceBitmap.trim();

        Path directPath = Paths.get(trimmed);

        if (directPath.isAbsolute()) {
            if (Files.isRegularFile(directPath)) {
                return directPath;
            }

            return null;
        }

        List<Path> candidates = new ArrayList<>();

        if (csvSourceFile != null && csvSourceFile.getParent() != null) {
            candidates.add(csvSourceFile.getParent().resolve(trimmed));
        }

        Path referenceImagesRoot = Paths.get("C:\\Opulo\\Data\\Reference Images");

        if (point != null && point.getName() != null && !point.getName().trim().isEmpty()) {
            candidates.add(referenceImagesRoot.resolve(point.getName().trim()).resolve(trimmed));
        }

        candidates.add(referenceImagesRoot.resolve(trimmed));

        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }

        return findFileBelow(referenceImagesRoot, trimmed, 4);
    }

    public static String describeReferenceSearch(GantryTestCsvInput input, GantryTestPoint point) {
        Path found = findReferenceBitmap(input, point);

        if (found != null) {
            return "Ref_bmp file found = " + found;
        }

        return String.format(Locale.US,
                "Ref_bmp file not found. Ref_bmp=\"%s\". Checked CSV folder and C:\\Opulo\\Data\\Reference Images.",
                point.getReferenceBitmap());
    }

    private static Path findFileBelow(Path root, String filename, int maxDepth) {
        if (root == null || filename == null || !Files.isDirectory(root)) {
            return null;
        }

        try (Stream<Path> stream = Files.walk(root, maxDepth)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName() != null)
                    .filter(path -> filename.equalsIgnoreCase(path.getFileName().toString()))
                    .findFirst()
                    .orElse(null);
        }
        catch (IOException e) {
            return null;
        }
    }
}