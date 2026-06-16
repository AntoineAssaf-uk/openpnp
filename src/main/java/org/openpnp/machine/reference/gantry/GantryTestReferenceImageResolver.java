package org.openpnp.machine.reference.gantry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

public final class GantryTestReferenceImageResolver {
    private GantryTestReferenceImageResolver() {
    }

    public static Path findReferenceBitmap(GantryTestCsvInput input, GantryTestPoint point) {
        return findReferenceBitmap(point.getReferenceBitmap());
    }

    public static Path findReferenceBitmap(String referenceBitmap) {
        if (referenceBitmap == null || referenceBitmap.trim().isEmpty()) {
            return null;
        }

        Path referenceBitmapPath = Paths.get(referenceBitmap.trim());

        if (!referenceBitmapPath.isAbsolute()) {
            return null;
        }

        if (!Files.isRegularFile(referenceBitmapPath)) {
            return null;
        }

        return referenceBitmapPath;
    }

    public static String describeReferenceSearch(GantryTestCsvInput input, GantryTestPoint point) {
        Path found = findReferenceBitmap(input, point);

        if (found != null) {
            return "Ref_Bmp absolute file found = " + found;
        }

        return String.format(Locale.US,
                "Ref_Bmp absolute file not found. Ref_Bmp=\"%s\".",
                point.getReferenceBitmap());
    }
}