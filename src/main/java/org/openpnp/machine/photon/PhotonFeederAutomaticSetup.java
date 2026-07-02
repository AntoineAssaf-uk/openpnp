package org.openpnp.machine.photon;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;

import org.openpnp.machine.reference.capture.ReferenceImageCaptureService;
import org.openpnp.machine.reference.imageoffset.CsImageOffsetResult;
import org.openpnp.machine.reference.imageoffset.ReferenceImageOffsetService;
import org.openpnp.model.Configuration;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.spi.Camera;
import org.openpnp.spi.Feeder;
import org.openpnp.spi.Head;
import org.openpnp.spi.Machine;
import org.openpnp.util.MovableUtils;

public class PhotonFeederAutomaticSetup {
    private static final String HEAD_NAME = "H1";
    private static final double MOVE_SPEED = 1.00;
    private static final Path CALIBRATION_FOLDER = Paths.get("C:\\Opulo\\Data\\Calibration");
    private static final DateTimeFormatter OUTPUT_FOLDER_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss",
            Locale.US);

    private PhotonFeederAutomaticSetup() {
    }

    public static SearchResult searchAndCollectValidFeeders(
            PhotonFeeder.FeederSearchProgressConsumer progressUpdate) throws Exception {
        PhotonFeeder.findAllFeeders(progressUpdate);
        return collectValidFeeders();
    }

    public static FirstFeederOffsetMeasurementResult searchAndMeasureFirstValidFeederOffset(
            PhotonFeederCalibrationCsv.CalibrationData calibrationData,
            PhotonFeeder.FeederSearchProgressConsumer progressUpdate) throws Exception {
        if (calibrationData == null) {
            throw new Exception("Calibration CSV data is null.");
        }

        SearchResult searchResult = searchAndCollectValidFeeders(progressUpdate);

        FeederSummary firstValidFeeder = null;
        for (FeederSummary feederSummary : searchResult.getFeederSummaries()) {
            if (feederSummary.isValid()) {
                firstValidFeeder = feederSummary;
                break;
            }
        }

        if (firstValidFeeder == null) {
            throw new Exception("No valid Photon feeders were found for automatic setup.");
        }

        Path outputFolder = createOutputFolderIfNeeded(calibrationData);

        OffsetMeasurementResult offsetMeasurementResult = measureFeederFiducialOffset(firstValidFeeder, calibrationData,
                1, outputFolder);

        return new FirstFeederOffsetMeasurementResult(
                searchResult,
                firstValidFeeder,
                offsetMeasurementResult);
    }

    public static FirstFeederXyCorrectionResult searchAndCorrectFirstValidFeederXy(
            PhotonFeederCalibrationCsv.CalibrationData calibrationData,
            PhotonFeeder.FeederSearchProgressConsumer progressUpdate) throws Exception {
        if (calibrationData == null) {
            throw new Exception("Calibration CSV data is null.");
        }

        SearchResult searchResult = searchAndCollectValidFeeders(progressUpdate);

        FeederSummary firstValidFeeder = null;
        for (FeederSummary feederSummary : searchResult.getFeederSummaries()) {
            if (feederSummary.isValid()) {
                firstValidFeeder = feederSummary;
                break;
            }
        }

        if (firstValidFeeder == null) {
            throw new Exception("No valid Photon feeders were found for automatic setup.");
        }

        Path outputFolder = createOutputFolderIfNeeded(calibrationData);
        List<OffsetMeasurementResult> measurements = new ArrayList<>();
        int correctionsApplied = 0;
        boolean success = false;

        for (int tentative = 1; tentative <= calibrationData.getTentatives(); tentative++) {
            OffsetMeasurementResult measurement = measureFeederFiducialOffset(
                    firstValidFeeder,
                    calibrationData,
                    tentative,
                    outputFolder);

            measurements.add(measurement);

            if (measurement.isWithinPrecision()) {
                success = true;
                break;
            }

            applyFeederSlotXyCorrection(firstValidFeeder, measurement);
            correctionsApplied++;
        }

        Location finalSlotLocation = getCurrentSlotLocation(firstValidFeeder);

        boolean configurationSaved = false;
        if (success) {
            Configuration.get().save();
            configurationSaved = true;
        }

        return new FirstFeederXyCorrectionResult(
                searchResult,
                firstValidFeeder,
                measurements,
                success,
                correctionsApplied,
                finalSlotLocation,
                outputFolder,
                configurationSaved);
    }

    public static SearchResult collectValidFeeders() {
        List<FeederSummary> feederSummaries = new ArrayList<>();

        for (Feeder feeder : Configuration.get().getMachine().getFeeders()) {
            if (!(feeder instanceof PhotonFeeder)) {
                continue;
            }

            PhotonFeeder photonFeeder = (PhotonFeeder) feeder;
            feederSummaries.add(createFeederSummary(photonFeeder));
        }

        Collections.sort(feederSummaries, (left, right) -> {
            Integer leftSlot = left.getSlotAddress();
            Integer rightSlot = right.getSlotAddress();

            if (leftSlot == null && rightSlot == null) {
                return left.getDisplayName().compareToIgnoreCase(right.getDisplayName());
            }
            if (leftSlot == null) {
                return 1;
            }
            if (rightSlot == null) {
                return -1;
            }
            return leftSlot.compareTo(rightSlot);
        });

        int validCount = 0;
        for (FeederSummary feederSummary : feederSummaries) {
            if (feederSummary.isValid()) {
                validCount++;
            }
        }

        return new SearchResult(feederSummaries, validCount);
    }

    private static OffsetMeasurementResult measureFeederFiducialOffset(
            FeederSummary feederSummary,
            PhotonFeederCalibrationCsv.CalibrationData calibrationData,
            int tentative,
            Path outputFolder) throws Exception {
        Machine machine = Configuration.get().getMachine();
        if (machine == null) {
            throw new Exception("No OpenPnP machine configuration is loaded.");
        }
        if (!machine.isEnabled()) {
            throw new Exception("Machine is not enabled. Enable the machine before automatic feeder setup.");
        }
        if (!machine.isHomed()) {
            throw new Exception("Machine is not homed. Home the machine before automatic feeder setup.");
        }

        Head head = machine.getHeadByName(HEAD_NAME);
        if (head == null) {
            throw new Exception("Machine head " + HEAD_NAME + " was not found.");
        }

        Camera topCamera = head.getDefaultCamera();
        if (topCamera == null) {
            throw new Exception("Head " + HEAD_NAME + " does not have a default Top camera.");
        }

        Location slotLocationMm = getCurrentSlotLocation(feederSummary);

        Location cameraTargetLocation = new Location(
                LengthUnit.Millimeters,
                slotLocationMm.getX(),
                slotLocationMm.getY(),
                Double.NaN,
                Double.NaN);

        head.moveToSafeZ();
        MovableUtils.moveToLocationAtSafeZ(topCamera, cameraTargetLocation, MOVE_SPEED);
        MovableUtils.fireTargetedUserAction(topCamera);

        BufferedImage originalImage = topCamera.lightSettleAndCapture();
        if (originalImage == null) {
            throw new Exception("Top camera capture returned null image for slot "
                    + feederSummary.getSlotAddress() + ".");
        }

        BufferedImage monoImage = ReferenceImageCaptureService.createGrayscaleLuminosityImage(originalImage);
        BufferedImage cropImage = ReferenceImageCaptureService.cropCentered(
                monoImage,
                calibrationData.getCrop());

        if (cropImage == null) {
            throw new Exception(String.format(Locale.US,
                    "Cannot create %d x %d crop from Top camera image %d x %d.",
                    calibrationData.getCrop(),
                    calibrationData.getCrop(),
                    monoImage.getWidth(),
                    monoImage.getHeight()));
        }

        BufferedImage referenceImage = ImageIO.read(calibrationData.getReferenceImagePath().toFile());
        if (referenceImage == null) {
            throw new Exception("Unable to read reference image: "
                    + calibrationData.getReferenceImagePath());
        }

        CsImageOffsetResult imageOffsetResult = ReferenceImageOffsetService.findOffset(referenceImage, cropImage);

        Location unitsPerPixel = topCamera.getUnitsPerPixelAtZ()
                .convertToUnits(LengthUnit.Millimeters);

        double dxMm = imageOffsetResult.getDx() * unitsPerPixel.getX();
        double dyMm = imageOffsetResult.getDy() * unitsPerPixel.getY();
        double dxUm = dxMm * 1000.0;
        double dyUm = dyMm * 1000.0;

        Path savedCropFile = null;

        if (outputFolder != null) {
            savedCropFile = outputFolder.resolve(String.format(Locale.US,
                    "Slot_%d_Tentative_%d_Crop_%d.bmp",
                    feederSummary.getSlotAddress(),
                    tentative,
                    calibrationData.getCrop()));

            saveBmp(cropImage, savedCropFile);
        }

        boolean withinPrecision = Math.abs(dxUm) <= calibrationData.getPrecisionUm()
                && Math.abs(dyUm) <= calibrationData.getPrecisionUm();

        return new OffsetMeasurementResult(
                tentative,
                calibrationData.getCrop(),
                slotLocationMm,
                topCamera.getLocation().convertToUnits(LengthUnit.Millimeters),
                unitsPerPixel,
                imageOffsetResult.getDx(),
                imageOffsetResult.getDy(),
                dxMm,
                dyMm,
                dxUm,
                dyUm,
                imageOffsetResult.getPeak(),
                imageOffsetResult.getDt(),
                withinPrecision,
                outputFolder,
                savedCropFile);
    }

    private static Path createOutputFolderIfNeeded(
            PhotonFeederCalibrationCsv.CalibrationData calibrationData) throws Exception {
        if (!calibrationData.isSaveImages()) {
            return null;
        }

        String timestamp = LocalDateTime.now().format(OUTPUT_FOLDER_TIMESTAMP);
        Path outputFolder = CALIBRATION_FOLDER.resolve(timestamp);
        Files.createDirectories(outputFolder);
        return outputFolder;
    }

    private static Location getCurrentSlotLocation(FeederSummary feederSummary) throws Exception {
        if (feederSummary == null || feederSummary.getFeeder() == null) {
            throw new Exception("Feeder summary is missing.");
        }

        if (feederSummary.getFeeder().getSlot() == null) {
            throw new Exception("Slot object is missing for slot " + feederSummary.getSlotAddress() + ".");
        }

        Location slotLocation = feederSummary.getFeeder().getSlot().getLocation();
        if (slotLocation == null) {
            throw new Exception("Slot location is null for slot " + feederSummary.getSlotAddress() + ".");
        }

        return slotLocation.convertToUnits(LengthUnit.Millimeters);
    }

    private static Location applyFeederSlotXyCorrection(
            FeederSummary feederSummary,
            OffsetMeasurementResult measurement) throws Exception {
        Location oldSlotLocation = getCurrentSlotLocation(feederSummary);

        Location correctedSlotLocation = oldSlotLocation.derive(
                oldSlotLocation.getX() + measurement.getDxMm(),
                oldSlotLocation.getY() + measurement.getDyMm(),
                null,
                null);

        feederSummary.getFeeder().getSlot().setLocation(correctedSlotLocation);

        return correctedSlotLocation;
    }

    private static void saveBmp(BufferedImage image, Path file) throws Exception {
        if (image == null) {
            throw new Exception("Cannot save BMP file because image is null: " + file);
        }
        boolean ok = ImageIO.write(image, "bmp", file.toFile());
        if (!ok) {
            throw new Exception("No BMP image writer is available for file: " + file);
        }
    }

    private static FeederSummary createFeederSummary(PhotonFeeder feeder) {
        String displayName = feeder.getName();
        String hardwareId = feeder.getHardwareId();
        Integer slotAddress = feeder.getSlotAddress();
        Location slotLocation = null;

        if (slotAddress != null && feeder.getSlot() != null) {
            slotLocation = feeder.getSlot().getLocation();
        }

        boolean valid = true;
        String invalidReason = "";

        if (hardwareId == null || hardwareId.trim().isEmpty()) {
            valid = false;
            invalidReason = "unconfigured feeder";
        } else if (slotAddress == null) {
            valid = false;
            invalidReason = "slot is None";
        } else if (feeder.getSlot() == null) {
            valid = false;
            invalidReason = "slot object is missing";
        } else if (slotLocation == null) {
            valid = false;
            invalidReason = "slot location is not configured";
        }

        return new FeederSummary(
                feeder,
                displayName,
                hardwareId,
                slotAddress,
                slotLocation,
                valid,
                invalidReason);
    }

    public static class FirstFeederXyCorrectionResult {
        private final SearchResult searchResult;
        private final FeederSummary feederSummary;
        private final List<OffsetMeasurementResult> measurements;
        private final boolean success;
        private final int correctionsApplied;
        private final Location finalSlotLocation;
        private final Path outputFolder;
        private final boolean configurationSaved;

        private FirstFeederXyCorrectionResult(
                SearchResult searchResult,
                FeederSummary feederSummary,
                List<OffsetMeasurementResult> measurements,
                boolean success,
                int correctionsApplied,
                Location finalSlotLocation,
                Path outputFolder,
                boolean configurationSaved) {
            this.searchResult = searchResult;
            this.feederSummary = feederSummary;
            this.measurements = new ArrayList<>(measurements);
            this.success = success;
            this.correctionsApplied = correctionsApplied;
            this.finalSlotLocation = finalSlotLocation;
            this.outputFolder = outputFolder;
            this.configurationSaved = configurationSaved;
        }

        public SearchResult getSearchResult() {
            return searchResult;
        }

        public FeederSummary getFeederSummary() {
            return feederSummary;
        }

        public List<OffsetMeasurementResult> getMeasurements() {
            return Collections.unmodifiableList(measurements);
        }

        public boolean isSuccess() {
            return success;
        }

        public int getCorrectionsApplied() {
            return correctionsApplied;
        }

        public Location getFinalSlotLocation() {
            return finalSlotLocation;
        }

        public Path getOutputFolder() {
            return outputFolder;
        }

        public boolean isConfigurationSaved() {
            return configurationSaved;
        }
    }

    public static class FirstFeederOffsetMeasurementResult {
        private final SearchResult searchResult;
        private final FeederSummary feederSummary;
        private final OffsetMeasurementResult offsetMeasurementResult;

        private FirstFeederOffsetMeasurementResult(
                SearchResult searchResult,
                FeederSummary feederSummary,
                OffsetMeasurementResult offsetMeasurementResult) {
            this.searchResult = searchResult;
            this.feederSummary = feederSummary;
            this.offsetMeasurementResult = offsetMeasurementResult;
        }

        public SearchResult getSearchResult() {
            return searchResult;
        }

        public FeederSummary getFeederSummary() {
            return feederSummary;
        }

        public OffsetMeasurementResult getOffsetMeasurementResult() {
            return offsetMeasurementResult;
        }
    }

    public static class OffsetMeasurementResult {
        private final int tentative;
        private final int crop;
        private final Location slotLocation;
        private final Location finalCameraLocation;
        private final Location cameraUnitsPerPixel;
        private final double dxPixels;
        private final double dyPixels;
        private final double dxMm;
        private final double dyMm;
        private final double dxUm;
        private final double dyUm;
        private final double peak;
        private final long dtMs;
        private final boolean withinPrecision;
        private final Path outputFolder;
        private final Path savedCropFile;

        private OffsetMeasurementResult(
                int tentative,
                int crop,
                Location slotLocation,
                Location finalCameraLocation,
                Location cameraUnitsPerPixel,
                double dxPixels,
                double dyPixels,
                double dxMm,
                double dyMm,
                double dxUm,
                double dyUm,
                double peak,
                long dtMs,
                boolean withinPrecision,
                Path outputFolder,
                Path savedCropFile) {
            this.tentative = tentative;
            this.crop = crop;
            this.slotLocation = slotLocation;
            this.finalCameraLocation = finalCameraLocation;
            this.cameraUnitsPerPixel = cameraUnitsPerPixel;
            this.dxPixels = dxPixels;
            this.dyPixels = dyPixels;
            this.dxMm = dxMm;
            this.dyMm = dyMm;
            this.dxUm = dxUm;
            this.dyUm = dyUm;
            this.peak = peak;
            this.dtMs = dtMs;
            this.withinPrecision = withinPrecision;
            this.outputFolder = outputFolder;
            this.savedCropFile = savedCropFile;
        }

        public int getTentative() {
            return tentative;
        }

        public int getCrop() {
            return crop;
        }

        public Location getSlotLocation() {
            return slotLocation;
        }

        public Location getFinalCameraLocation() {
            return finalCameraLocation;
        }

        public Location getCameraUnitsPerPixel() {
            return cameraUnitsPerPixel;
        }

        public double getDxPixels() {
            return dxPixels;
        }

        public double getDyPixels() {
            return dyPixels;
        }

        public double getDxMm() {
            return dxMm;
        }

        public double getDyMm() {
            return dyMm;
        }

        public double getDxUm() {
            return dxUm;
        }

        public double getDyUm() {
            return dyUm;
        }

        public double getPeak() {
            return peak;
        }

        public long getDtMs() {
            return dtMs;
        }

        public boolean isWithinPrecision() {
            return withinPrecision;
        }

        public Path getOutputFolder() {
            return outputFolder;
        }

        public Path getSavedCropFile() {
            return savedCropFile;
        }
    }

    public static class SearchResult {
        private final List<FeederSummary> feederSummaries;
        private final int validCount;

        private SearchResult(
                List<FeederSummary> feederSummaries,
                int validCount) {
            this.feederSummaries = new ArrayList<>(feederSummaries);
            this.validCount = validCount;
        }

        public List<FeederSummary> getFeederSummaries() {
            return Collections.unmodifiableList(feederSummaries);
        }

        public int getTotalCount() {
            return feederSummaries.size();
        }

        public int getValidCount() {
            return validCount;
        }

        public int getInvalidCount() {
            return feederSummaries.size() - validCount;
        }
    }

    public static class FeederSummary {
        private final PhotonFeeder feeder;
        private final String displayName;
        private final String hardwareId;
        private final Integer slotAddress;
        private final Location slotLocation;
        private final boolean valid;
        private final String invalidReason;

        private FeederSummary(
                PhotonFeeder feeder,
                String displayName,
                String hardwareId,
                Integer slotAddress,
                Location slotLocation,
                boolean valid,
                String invalidReason) {
            this.feeder = feeder;
            this.displayName = displayName;
            this.hardwareId = hardwareId;
            this.slotAddress = slotAddress;
            this.slotLocation = slotLocation;
            this.valid = valid;
            this.invalidReason = invalidReason;
        }

        public PhotonFeeder getFeeder() {
            return feeder;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getHardwareId() {
            return hardwareId;
        }

        public Integer getSlotAddress() {
            return slotAddress;
        }

        public Location getSlotLocation() {
            return slotLocation;
        }

        public boolean isValid() {
            return valid;
        }

        public String getInvalidReason() {
            return invalidReason;
        }
    }
}