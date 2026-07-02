package org.openpnp.machine.photon;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.awt.image.BufferedImage;

import javax.imageio.ImageIO;

import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.model.Configuration;
import org.openpnp.spi.Head;
import org.openpnp.spi.Nozzle;

public class PhotonFeederCalibrationCsv {
    public static final Path CSV_PATH = Paths.get(
            "C:\\Opulo\\Data\\Calibration\\Photon Feeder Fiducial Data.CSV");

    private static final String KEY_PHOTON_FEEDER = "PhotonFeeder";
    private static final String KEY_SAVE_IMAGES = "Save Images";
    private static final DateTimeFormatter CSV_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.US);

    private PhotonFeederCalibrationCsv() {
    }

    public static CalibrationData readAndValidate() throws Exception {
        return readAndValidate(CSV_PATH);
    }

    public static CalibrationData readAndValidate(Path csvPath) throws Exception {
        if (!Files.exists(csvPath)) {
            throw new Exception("CSV file does not exist: " + csvPath);
        }

        List<String> lines = Files.readAllLines(csvPath);

        int photonFeederLine = -1;
        List<String> photonFeederRow = null;

        for (int i = 0; i < lines.size(); i++) {
            if (isBlankLine(lines.get(i))) {
                continue;
            }

            List<String> row = splitCsvLine(lines.get(i));
            if (KEY_PHOTON_FEEDER.equalsIgnoreCase(getCell(row, 0))) {
                photonFeederLine = i;
                photonFeederRow = row;
                break;
            }
        }

        if (photonFeederLine < 0 || photonFeederRow == null) {
            throw new Exception("CSV does not contain a PhotonFeeder row in column A.");
        }

        int saveImagesLine = -1;
        List<String> saveImagesRow = null;

        for (int i = photonFeederLine + 1; i < lines.size(); i++) {
            if (isBlankLine(lines.get(i))) {
                continue;
            }

            List<String> row = splitCsvLine(lines.get(i));
            if (KEY_SAVE_IMAGES.equalsIgnoreCase(getCell(row, 0))) {
                saveImagesLine = i;
                saveImagesRow = row;
                break;
            }

            throw new Exception(String.format(
                    "Expected \"%s\" row after PhotonFeeder row, but found \"%s\" at CSV line %d.",
                    KEY_SAVE_IMAGES,
                    getCell(row, 0),
                    i + 1));
        }

        if (saveImagesLine < 0 || saveImagesRow == null) {
            throw new Exception("CSV does not contain a Save Images row after PhotonFeeder row.");
        }

        String referenceImagePathText = getRequiredCell(
                photonFeederRow,
                1,
                "Feeder fiducial image path",
                photonFeederLine);

        String cropText = getRequiredCell(
                photonFeederRow,
                2,
                "Crop",
                photonFeederLine);

        String nozzleName = getRequiredCell(
                photonFeederRow,
                3,
                "Nozzle",
                photonFeederLine);

        String tentativesText = getRequiredCell(
                photonFeederRow,
                4,
                "Tentatives",
                photonFeederLine);

        String precisionText = getRequiredCell(
                photonFeederRow,
                5,
                "Precision (um)",
                photonFeederLine);

        String saveImagesText = getRequiredCell(
                saveImagesRow,
                1,
                "Save Images yes/no",
                saveImagesLine);

        Path referenceImagePath = Paths.get(referenceImagePathText);
        if (!Files.exists(referenceImagePath)) {
            throw new Exception("Reference image file does not exist: " + referenceImagePath);
        }

        int crop = parseInteger(cropText, "Crop", photonFeederLine);
        if (crop != 1024 && crop != 512 && crop != 256) {
            throw new Exception(String.format(
                    "Invalid crop value %d at CSV line %d. Expected 1024, 512, or 256.",
                    crop,
                    photonFeederLine + 1));
        }
        
        BufferedImage referenceImage = ImageIO.read(referenceImagePath.toFile());
        if (referenceImage == null) {
            throw new Exception("Unable to read reference image: " + referenceImagePath);
        }

        if (referenceImage.getWidth() != crop || referenceImage.getHeight() != crop) {
            throw new Exception(String.format(
                    "Reference image size does not match CSV crop value.%n%n"
                            + "Reference image: %s%n"
                            + "Image size: %d x %d%n"
                            + "CSV crop: %d x %d",
                    referenceImagePath,
                    referenceImage.getWidth(),
                    referenceImage.getHeight(),
                    crop,
                    crop));
        }

        int tentatives = parseInteger(tentativesText, "Tentatives", photonFeederLine);
        if (tentatives <= 0) {
            throw new Exception(String.format(
                    "Tentatives must be > 0 at CSV line %d.",
                    photonFeederLine + 1));
        }

        double precisionUm = parseDouble(precisionText, "Precision (um)", photonFeederLine);
        if (precisionUm <= 0.0) {
            throw new Exception(String.format(
                    "Precision (um) must be > 0 at CSV line %d.",
                    photonFeederLine + 1));
        }

        boolean saveImages = parseYesNo(saveImagesText, saveImagesLine);

        Head head = Configuration.get().getMachine().getHeadByName("H1");
        if (head == null) {
            throw new Exception("Machine head H1 was not found.");
        }

        Nozzle nozzle = head.getNozzleByName(nozzleName);
        if (nozzle == null) {
            throw new Exception("Nozzle \"" + nozzleName + "\" was not found on head H1.");
        }

        List<String> preservedInputLines = new ArrayList<>(
                lines.subList(0, saveImagesLine + 1));

        return new CalibrationData(
                csvPath,
                preservedInputLines,
                photonFeederLine + 1,
                saveImagesLine + 1,
                referenceImagePath,
                crop,
                nozzleName,
                tentatives,
                precisionUm,
                saveImages);
    }

    public static String createIssuedOnTimestamp() {
        return LocalDateTime.now().format(CSV_TIMESTAMP);
    }

    public static void rewriteResults(
            CalibrationData calibrationData,
            String issuedOnTimestamp,
            List<FeederResultLine> resultLines) throws Exception {
        if (calibrationData == null) {
            throw new Exception("Calibration CSV data is null.");
        }

        if (issuedOnTimestamp == null || issuedOnTimestamp.trim().isEmpty()) {
            throw new Exception("CSV issued-on timestamp is empty.");
        }

        List<String> outputLines = new ArrayList<>(
                calibrationData.getPreservedInputLines());

        if (outputLines.isEmpty()) {
            outputLines.add("Issued on " + issuedOnTimestamp);
        } else {
            outputLines.set(0, "Issued on " + issuedOnTimestamp);
        }

        outputLines.add("");

        if (resultLines != null) {
            for (FeederResultLine resultLine : resultLines) {
                outputLines.add(resultLine.toCsvLine());
            }
        }

        Files.write(calibrationData.getCsvPath(), outputLines);
    }

    private static boolean isBlankLine(String line) {
        return line == null || line.trim().isEmpty();
    }

    private static List<String> splitCsvLine(String line) {
        String[] tokens = line.split(",", -1);
        List<String> cells = new ArrayList<>();
        for (String token : tokens) {
            cells.add(token.trim());
        }
        return cells;
    }

    private static String getCell(List<String> row, int index) {
        if (index < 0 || index >= row.size()) {
            return "";
        }
        return row.get(index);
    }

    private static String getRequiredCell(
            List<String> row,
            int index,
            String name,
            int zeroBasedLineNumber) throws Exception {
        String value = getCell(row, index);
        if (value == null || value.trim().isEmpty()) {
            throw new Exception(String.format(
                    "Missing %s at CSV line %d, column %s.",
                    name,
                    zeroBasedLineNumber + 1,
                    columnName(index)));
        }
        return value.trim();
    }

    private static int parseInteger(
            String value,
            String name,
            int zeroBasedLineNumber) throws Exception {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new Exception(String.format(
                    "Invalid integer for %s at CSV line %d: %s",
                    name,
                    zeroBasedLineNumber + 1,
                    value));
        }
    }

    private static double parseDouble(
            String value,
            String name,
            int zeroBasedLineNumber) throws Exception {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            throw new Exception(String.format(
                    "Invalid number for %s at CSV line %d: %s",
                    name,
                    zeroBasedLineNumber + 1,
                    value));
        }
    }

    private static boolean parseYesNo(
            String value,
            int zeroBasedLineNumber) throws Exception {
        String normalized = value.trim().toLowerCase();
        if ("yes".equals(normalized)) {
            return true;
        }
        if ("no".equals(normalized)) {
            return false;
        }
        throw new Exception(String.format(
                "Invalid Save Images value at CSV line %d: %s. Expected yes or no.",
                zeroBasedLineNumber + 1,
                value));
    }

    private static String columnName(int index) {
        return String.valueOf((char) ('A' + index));
    }

    public static class FeederResultLine {
        private final int slotAddress;
        private final double x;
        private final double y;
        private final double z;
        private final boolean error;

        private FeederResultLine(
                int slotAddress,
                double x,
                double y,
                double z,
                boolean error) {
            this.slotAddress = slotAddress;
            this.x = x;
            this.y = y;
            this.z = z;
            this.error = error;
        }

        public static FeederResultLine create(
                int slotAddress,
                Location location,
                boolean error) throws Exception {
            if (location == null) {
                throw new Exception("Cannot create CSV result line because location is null.");
            }

            Location locationMm = location.convertToUnits(LengthUnit.Millimeters);

            return new FeederResultLine(
                    slotAddress,
                    locationMm.getX(),
                    locationMm.getY(),
                    locationMm.getZ(),
                    error);
        }

        public String toCsvLine() {
            if (error) {
                return String.format(Locale.US,
                        "Slot,%d,Location,%.3f,%.3f,%.3f,error",
                        slotAddress,
                        x,
                        y,
                        z);
            }

            return String.format(Locale.US,
                    "Slot,%d,Location,%.3f,%.3f,%.3f",
                    slotAddress,
                    x,
                    y,
                    z);
        }
    }

    public static class CalibrationData {
        private final Path csvPath;
        private final List<String> preservedInputLines;
        private final int photonFeederCsvLine;
        private final int saveImagesCsvLine;
        private final Path referenceImagePath;
        private final int crop;
        private final String nozzleName;
        private final int tentatives;
        private final double precisionUm;
        private final boolean saveImages;

        private CalibrationData(
                Path csvPath,
                List<String> preservedInputLines,
                int photonFeederCsvLine,
                int saveImagesCsvLine,
                Path referenceImagePath,
                int crop,
                String nozzleName,
                int tentatives,
                double precisionUm,
                boolean saveImages) {
            this.csvPath = csvPath;
            this.preservedInputLines = new ArrayList<>(preservedInputLines);
            this.photonFeederCsvLine = photonFeederCsvLine;
            this.saveImagesCsvLine = saveImagesCsvLine;
            this.referenceImagePath = referenceImagePath;
            this.crop = crop;
            this.nozzleName = nozzleName;
            this.tentatives = tentatives;
            this.precisionUm = precisionUm;
            this.saveImages = saveImages;
        }

        public Path getCsvPath() {
            return csvPath;
        }

        public List<String> getPreservedInputLines() {
            return Collections.unmodifiableList(preservedInputLines);
        }

        public int getPhotonFeederCsvLine() {
            return photonFeederCsvLine;
        }

        public int getSaveImagesCsvLine() {
            return saveImagesCsvLine;
        }

        public Path getReferenceImagePath() {
            return referenceImagePath;
        }

        public int getCrop() {
            return crop;
        }

        public String getNozzleName() {
            return nozzleName;
        }

        public int getTentatives() {
            return tentatives;
        }

        public double getPrecisionUm() {
            return precisionUm;
        }

        public boolean isSaveImages() {
            return saveImages;
        }
    }
}