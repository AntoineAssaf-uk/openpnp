package org.openpnp.machine.reference.gantry;

import java.util.Locale;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.openpnp.machine.reference.lookup.ReferenceMachineLookup;
import org.openpnp.spi.Camera;
import org.openpnp.spi.Head;
import org.openpnp.spi.Machine;
import org.openpnp.spi.Nozzle;

public final class GantryTestValidator {
    private static final double MIN_X_MM = 0.0;
    private static final double MAX_X_MM = 465.0;
    private static final double MIN_Y_MM = 0.0;
    private static final double MAX_Y_MM = 513.0;
    private static final double MIN_Z_MM = 0.0;
    private static final double MAX_Z_MM = 63.0;

    private GantryTestValidator() {
    }

    public static GantryTestValidationResult validate(GantryTestCsvInput input) {
        GantryTestValidationResult result = new GantryTestValidationResult();

        if (input == null) {
            result.addError("CSV input is null.");
            return result;
        }

        result.addMessage("Source file = " + input.getSourceFile());
        result.addMessage("Number of cycles = " + input.getNumberOfCycles());
        result.addMessage("Point count = " + input.getPoints().size());

        Machine machine;
        Head head;
        Camera topCamera;
        Camera bottomCamera;

        try {
            machine = ReferenceMachineLookup.getMachine();

            if (!machine.isEnabled()) {
                result.addError("Machine is not enabled.");
            } else {
                result.addMessage("Machine enabled = true");
            }

            if (!machine.isHomed()) {
                result.addError("Machine is not homed.");
            } else {
                result.addMessage("Machine homed = true");
            }

            head = ReferenceMachineLookup.findHead(machine, ReferenceMachineLookup.HEAD_H1);
            result.addMessage("Head found = " + head.getName());

            topCamera = ReferenceMachineLookup.findDefaultHeadCamera(head);
            result.addMessage("Top camera found = " + topCamera.getName());

            bottomCamera = ReferenceMachineLookup.findMachineCamera(machine, ReferenceMachineLookup.BOTTOM_CAMERA);
            result.addMessage("Bottom camera found = " + bottomCamera.getName());
        } catch (Exception e) {
            result.addError("Machine lookup failed: " + e.getMessage());
            return result;
        }

        if (input.getNumberOfCycles() <= 0) {
            result.addError("Number of cycles must be > 0.");
        }

        if (input.getPoints().isEmpty()) {
            result.addWarning("CSV contains no gantry test points.");
            return result;
        }

        int expectedIndex = 1;

        for (GantryTestPoint point : input.getPoints()) {
            validatePoint(result, head, point, expectedIndex, input.getSourceFile());
            expectedIndex++;
        }

        return result;
    }

    private static void validatePoint(
            GantryTestValidationResult result,
            Head head,
            GantryTestPoint point,
            int expectedIndex,
            Path csvSourceFile) {
        String row = String.format(Locale.US,
                "line %d, #=%d, Name=\"%s\"",
                point.getLineNumber(),
                point.getIndex(),
                point.getName());

        boolean rowPassed = true;

        if (point.getIndex() != expectedIndex) {
            result.addWarning(row + ": expected #=" + expectedIndex + " but found #=" + point.getIndex() + ".");
        }

        if (!isFinite(point.getX()) || point.getX() < MIN_X_MM || point.getX() > MAX_X_MM) {
            result.addError(row + ": X is outside safe range "
                    + MIN_X_MM + ".." + MAX_X_MM + " mm. X=" + point.getX());
            rowPassed = false;
        }

        if (!isFinite(point.getY()) || point.getY() < MIN_Y_MM || point.getY() > MAX_Y_MM) {
            result.addError(row + ": Y is outside safe range "
                    + MIN_Y_MM + ".." + MAX_Y_MM + " mm. Y=" + point.getY());
            rowPassed = false;
        }

        if (!isFinite(point.getNozzleZ()) || point.getNozzleZ() < MIN_Z_MM || point.getNozzleZ() > MAX_Z_MM) {
            result.addError(row + ": N_Z is outside safe range "
                    + MIN_Z_MM + ".." + MAX_Z_MM + " mm. N_Z=" + point.getNozzleZ());
            rowPassed = false;
        }

        try {
            Nozzle nozzle = ReferenceMachineLookup.findNozzle(head, point.getNozzleName());
            result.addMessage(row + ": Nozzle found = " + nozzle.getName());
        } catch (Exception e) {
            result.addError(row + ": Nozzle \"" + point.getNozzleName()
                    + "\" not found on head " + ReferenceMachineLookup.HEAD_H1 + ".");
            rowPassed = false;
        }

        if (!isValidCropFactor(point.getCropFactor())) {
            result.addError(row + ": Crop_factor must be 1024, 512, or 256. Crop_factor="
                    + point.getCropFactor());
            rowPassed = false;
        }

        if (!point.getTopBottom().equals("Top") && !point.getTopBottom().equals("Bot")) {
            result.addError(row + ": Top_Bot must be Top or Bot. Top_Bot=" + point.getTopBottom());
            rowPassed = false;
        }

        if (!validateReferenceBitmapName(result, row, point, csvSourceFile)) {
            rowPassed = false;
        }

        if (rowPassed) {
            result.addMessage(row + ": VALID.");
        }
    }

    private static boolean validateReferenceBitmapName(
            GantryTestValidationResult result,
            String row,
            GantryTestPoint point,
            Path csvSourceFile) {
        String referenceBitmap = point.getReferenceBitmap();

        if (referenceBitmap == null || referenceBitmap.trim().isEmpty()) {
            result.addError(row + ": Ref_bmp is empty.");
            return false;
        }

        String trimmed = referenceBitmap.trim();

        if (!trimmed.toLowerCase(Locale.US).endsWith(".bmp")) {
            result.addError(row + ": Ref_bmp must end with .bmp. Ref_bmp=\"" + trimmed + "\".");
            return false;
        }

        if (trimmed.contains("\\") || trimmed.contains("/")) {
            result.addWarning(row + ": Ref_bmp should normally be a filename only, not a full path. Ref_bmp=\""
                    + trimmed + "\".");
        }

        Path directPath = Paths.get(trimmed);

        if (directPath.isAbsolute()) {
            if (Files.isRegularFile(directPath)) {
                result.addMessage(row + ": Ref_bmp file found = " + directPath);
                return true;
            }

            result.addError(row + ": Ref_bmp file not found = " + directPath);
            return false;
        }

        Path foundPath = findReferenceBitmap(trimmed, point, csvSourceFile);

        if (foundPath != null) {
            result.addMessage(row + ": Ref_bmp file found = " + foundPath);
            return true;
        }

        result.addError(row + ": Ref_bmp file not found. Ref_bmp=\"" + trimmed
                + "\". Checked CSV folder and C:\\Opulo\\Data\\Reference Images.");
        return false;
    }

    private static Path findReferenceBitmap(String referenceBitmap, GantryTestPoint point, Path csvSourceFile) {
        List<Path> candidates = new ArrayList<>();

        if (csvSourceFile != null && csvSourceFile.getParent() != null) {
            candidates.add(csvSourceFile.getParent().resolve(referenceBitmap));
        }

        Path referenceImagesRoot = Paths.get("C:\\Opulo\\Data\\Reference Images");

        if (point.getName() != null && !point.getName().trim().isEmpty()) {
            candidates.add(referenceImagesRoot.resolve(point.getName().trim()).resolve(referenceBitmap));
        }

        candidates.add(referenceImagesRoot.resolve(referenceBitmap));

        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }

        return findFileBelow(referenceImagesRoot, referenceBitmap, 4);
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
        } catch (IOException e) {
            return null;
        }
    }

    private static boolean isValidCropFactor(int cropFactor) {
        return cropFactor == 1024 || cropFactor == 512 || cropFactor == 256;
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}