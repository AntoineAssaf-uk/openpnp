package org.openpnp.machine.photon.sheets.gui;

import com.jgoodies.forms.layout.ColumnSpec;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.FormSpecs;
import com.jgoodies.forms.layout.RowSpec;
import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.openpnp.gui.MainFrame;
import org.openpnp.gui.support.AbstractConfigurationWizard;
import org.openpnp.gui.support.MessageBoxes;
import org.openpnp.machine.photon.PhotonFeeder;
import org.openpnp.machine.photon.PhotonFeederAutomaticSetup;
import org.openpnp.machine.photon.PhotonFeederCalibrationCsv;
import org.openpnp.machine.photon.PhotonProperties;
import org.openpnp.model.Configuration;
import org.openpnp.util.UiUtils;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class GlobalConfigConfigurationWizard extends AbstractConfigurationWizard {
        private static final DateTimeFormatter LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

        private final PhotonProperties photonProperties;
        private final FeederSearchProgressBar progressBarPanel;
        private final JButton searchButton;
        private final JButton automaticFeederSetupButton;
        private final JButton eraseLogsButton;
        private final JSpinner maxFeederSpinner;
        private final JTextArea automaticFeederSetupLogTextArea;

        /**
         * Create the panel.
         */
        public GlobalConfigConfigurationWizard() {
                photonProperties = new PhotonProperties(Configuration.get().getMachine());

                JPanel searchPanel = new JPanel();
                searchPanel.setBorder(new TitledBorder(null, "Search",
                                TitledBorder.LEADING, TitledBorder.TOP, null, null));
                contentPanel.add(searchPanel);
                searchPanel.setLayout(new FormLayout(new ColumnSpec[] {
                                FormSpecs.RELATED_GAP_COLSPEC,
                                FormSpecs.DEFAULT_COLSPEC,
                                FormSpecs.RELATED_GAP_COLSPEC,
                                ColumnSpec.decode("50dlu"),
                                FormSpecs.RELATED_GAP_COLSPEC,
                                FormSpecs.DEFAULT_COLSPEC,
                                FormSpecs.RELATED_GAP_COLSPEC,
                                FormSpecs.DEFAULT_COLSPEC,
                                FormSpecs.RELATED_GAP_COLSPEC,
                                ColumnSpec.decode("120dlu:grow"),
                                FormSpecs.RELATED_GAP_COLSPEC,
                }, new RowSpec[] {
                                FormSpecs.RELATED_GAP_ROWSPEC,
                                FormSpecs.DEFAULT_ROWSPEC,
                                FormSpecs.RELATED_GAP_ROWSPEC,
                                FormSpecs.DEFAULT_ROWSPEC,
                                FormSpecs.RELATED_GAP_ROWSPEC,
                                RowSpec.decode("90dlu:grow"),
                                FormSpecs.RELATED_GAP_ROWSPEC,
                }));

                JLabel lblMaxFeeder = new JLabel("Maximum Feeder Address To Scan");
                searchPanel.add(lblMaxFeeder, "2, 2");

                int initialMaxFeederAddress = photonProperties.getMaxFeederAddress();
                SpinnerNumberModel maxFeederSpinnerModel = new SpinnerNumberModel(
                                initialMaxFeederAddress,
                                1,
                                254,
                                1);
                maxFeederSpinner = new JSpinner(maxFeederSpinnerModel);
                searchPanel.add(maxFeederSpinner, "4, 2");

                searchButton = new JButton("Search");
                searchButton.addActionListener(searchAction);
                searchPanel.add(searchButton, "6, 2");

                automaticFeederSetupButton = new JButton("Automatic feeder setup");
                automaticFeederSetupButton.addActionListener(automaticFeederSetupAction);
                searchPanel.add(automaticFeederSetupButton, "2, 4, 5, 1, fill, default");

                eraseLogsButton = new JButton("Erase Logs");
                eraseLogsButton.addActionListener(eraseLogsAction);
                searchPanel.add(eraseLogsButton, "8, 4");

                progressBarPanel = new FeederSearchProgressBar();
                searchPanel.add(progressBarPanel, "8, 2, 3, 1, fill, fill");
                progressBarPanel.setVisible(false);
                progressBarPanel.setNumberOfElements(initialMaxFeederAddress);

                automaticFeederSetupLogTextArea = new JTextArea();
                automaticFeederSetupLogTextArea.setEditable(false);
                automaticFeederSetupLogTextArea.setLineWrap(false);
                automaticFeederSetupLogTextArea.setRows(8);
                automaticFeederSetupLogTextArea.setFont(
                                new Font(Font.MONOSPACED, Font.PLAIN,
                                                automaticFeederSetupLogTextArea.getFont().getSize()));

                JScrollPane logScrollPane = new JScrollPane(automaticFeederSetupLogTextArea);
                logScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
                logScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
                searchPanel.add(logScrollPane, "2, 6, 9, 1, fill, fill");

                appendAutomaticFeederSetupLog("Automatic feeder setup log ready.");
        }

        @Override
        public void createBindings() {
                bind(UpdateStrategy.READ_WRITE,
                                photonProperties,
                                "maxFeederAddress",
                                maxFeederSpinner,
                                "value");
        }

        private void appendAutomaticFeederSetupLog(String message) {
                String timestamp = LocalDateTime.now().format(LOG_TIME_FORMAT);
                automaticFeederSetupLogTextArea.append(timestamp + "  " + message + System.lineSeparator());
                automaticFeederSetupLogTextArea.setCaretPosition(
                                automaticFeederSetupLogTextArea.getDocument().getLength());
        }

        private void setSearchControlsEnabled(boolean enabled) {
                searchButton.setEnabled(enabled);
                automaticFeederSetupButton.setEnabled(enabled);
                maxFeederSpinner.setEnabled(enabled);
        }

        private final Action searchAction = new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                        progressBarPanel.setVisible(true);
                        setSearchControlsEnabled(false);

                        int maxFeederAddress = photonProperties.getMaxFeederAddress();
                        progressBarPanel.setNumberOfElements(maxFeederAddress);

                        appendAutomaticFeederSetupLog(
                                        "Search started. Maximum feeder address = " + maxFeederAddress + ".");

                        UiUtils.submitUiMachineTask(() -> {
                                PhotonFeeder.findAllFeeders(progressBarPanel::updateFeederState);
                                return null;
                        }, (parameter) -> {
                                resetState();
                                appendAutomaticFeederSetupLog("Search completed.");
                        }, (throwable) -> {
                                resetState();
                                appendAutomaticFeederSetupLog("Search failed: " + throwable.getMessage());
                                MessageBoxes.errorBox(MainFrame.get(), "Error", throwable);
                        });
                }

                private void resetState() {
                        progressBarPanel.setVisible(false);
                        progressBarPanel.clearAllState();
                        setSearchControlsEnabled(true);
                }
        };

        private final Action automaticFeederSetupAction = new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                        appendAutomaticFeederSetupLog("Automatic feeder setup CSV validation started.");

                        PhotonFeederCalibrationCsv.CalibrationData calibrationData;
                        try {
                                calibrationData = PhotonFeederCalibrationCsv.readAndValidate();

                                appendAutomaticFeederSetupLog("CSV validation successful.");
                                appendAutomaticFeederSetupLog("CSV file: "
                                                + calibrationData.getCsvPath());
                                appendAutomaticFeederSetupLog("PhotonFeeder row: "
                                                + calibrationData.getPhotonFeederCsvLine());
                                appendAutomaticFeederSetupLog("Save Images row: "
                                                + calibrationData.getSaveImagesCsvLine());
                                appendAutomaticFeederSetupLog("Reference image: "
                                                + calibrationData.getReferenceImagePath());
                                appendAutomaticFeederSetupLog("Crop: "
                                                + calibrationData.getCrop());
                                appendAutomaticFeederSetupLog("Nozzle: "
                                                + calibrationData.getNozzleName());
                                appendAutomaticFeederSetupLog("Tentatives: "
                                                + calibrationData.getTentatives());
                                appendAutomaticFeederSetupLog(String.format(
                                                "Precision: %.3f um",
                                                calibrationData.getPrecisionUm()));
                                appendAutomaticFeederSetupLog("Save Images: "
                                                + (calibrationData.isSaveImages() ? "yes" : "no"));
                                appendAutomaticFeederSetupLog("Preserved CSV input lines: "
                                                + calibrationData.getPreservedInputLines().size());
                        } catch (Exception ex) {
                                appendAutomaticFeederSetupLog("CSV validation failed: " + ex.getMessage());
                                MessageBoxes.errorBox(
                                                MainFrame.get(),
                                                "Automatic feeder setup CSV error",
                                                ex);
                                return;
                        }

                        progressBarPanel.setVisible(true);
                        setSearchControlsEnabled(false);

                        int maxFeederAddress = photonProperties.getMaxFeederAddress();
                        progressBarPanel.setNumberOfElements(maxFeederAddress);

                        appendAutomaticFeederSetupLog(
                                        "Photon feeder search started. Maximum feeder address = "
                                                        + maxFeederAddress + ".");

                        UiUtils.submitUiMachineTask(() -> {
                                return PhotonFeederAutomaticSetup.searchAndCorrectAllValidFeedersXyAndZ(
                                                calibrationData,
                                                progressBarPanel::updateFeederState);
                        }, (allFeedersResult) -> {
                                progressBarPanel.setVisible(false);
                                progressBarPanel.clearAllState();
                                setSearchControlsEnabled(true);

                                PhotonFeederAutomaticSetup.SearchResult searchResult = allFeedersResult
                                                .getSearchResult();

                                appendAutomaticFeederSetupLog("Photon feeder search completed.");
                                appendAutomaticFeederSetupLog(String.format(
                                                "Photon feeders listed: %d, valid for automatic setup: %d, invalid/skipped: %d.",
                                                searchResult.getTotalCount(),
                                                searchResult.getValidCount(),
                                                searchResult.getInvalidCount()));

                                for (PhotonFeederAutomaticSetup.FeederSummary feederSummary : searchResult
                                                .getFeederSummaries()) {
                                        if (feederSummary.isValid()) {
                                                appendAutomaticFeederSetupLog(String.format(
                                                                "VALID  Slot %d  %s  Initial Location X=%.3f Y=%.3f Z=%.3f",
                                                                feederSummary.getSlotAddress(),
                                                                feederSummary.getHardwareId(),
                                                                feederSummary.getSlotLocation().getX(),
                                                                feederSummary.getSlotLocation().getY(),
                                                                feederSummary.getSlotLocation().getZ()));
                                        } else {
                                                appendAutomaticFeederSetupLog(String.format(
                                                                "SKIP   Slot %s  %s  Reason: %s",
                                                                feederSummary.getSlotAddress() == null
                                                                                ? "None"
                                                                                : feederSummary.getSlotAddress()
                                                                                                .toString(),
                                                                feederSummary.getDisplayName(),
                                                                feederSummary.getInvalidReason()));
                                        }
                                }

                                appendAutomaticFeederSetupLog(String.format(
                                                "6.4.7 running XY and Z setup for all valid Photon feeders. Processed feeders: %d.",
                                                allFeedersResult.getProcessedFeederCount()));

                                if (allFeedersResult.getOutputFolder() != null) {
                                        appendAutomaticFeederSetupLog("Saved captured crops in: "
                                                        + allFeedersResult.getOutputFolder());
                                }

                                for (PhotonFeederAutomaticSetup.FirstFeederXyAndZCorrectionResult feederResult : allFeedersResult
                                                .getFeederResults()) {
                                        PhotonFeederAutomaticSetup.FirstFeederXyCorrectionResult xyCorrectionResult = feederResult
                                                        .getXyCorrectionResult();

                                        PhotonFeederAutomaticSetup.FeederSummary measuredFeeder = xyCorrectionResult
                                                        .getFeederSummary();

                                        appendAutomaticFeederSetupLog(String.format(
                                                        "Slot %d / hardware %s automatic setup completed.",
                                                        measuredFeeder.getSlotAddress(),
                                                        measuredFeeder.getHardwareId()));

                                        for (PhotonFeederAutomaticSetup.OffsetMeasurementResult offsetResult : xyCorrectionResult
                                                        .getMeasurements()) {
                                                appendAutomaticFeederSetupLog(String.format(
                                                                "Slot %d Tentative %d/%d: Location X=%.3f Y=%.3f Z=%.3f",
                                                                measuredFeeder.getSlotAddress(),
                                                                offsetResult.getTentative(),
                                                                calibrationData.getTentatives(),
                                                                offsetResult.getSlotLocation().getX(),
                                                                offsetResult.getSlotLocation().getY(),
                                                                offsetResult.getSlotLocation().getZ()));

                                                appendAutomaticFeederSetupLog(String.format(
                                                                "Slot %d Tentative %d: dx=%.3f um, dy=%.3f um, peak=%.9f, dt=%d ms, precision=%s",
                                                                measuredFeeder.getSlotAddress(),
                                                                offsetResult.getTentative(),
                                                                offsetResult.getDxUm(),
                                                                offsetResult.getDyUm(),
                                                                offsetResult.getPeak(),
                                                                offsetResult.getDtMs(),
                                                                offsetResult.isWithinPrecision() ? "PASS" : "FAIL"));

                                                if (!offsetResult.isWithinPrecision()) {
                                                        appendAutomaticFeederSetupLog(String.format(
                                                                        "Slot %d Tentative %d: Applied X = X + dx, Y = Y + dy.",
                                                                        measuredFeeder.getSlotAddress(),
                                                                        offsetResult.getTentative()));
                                                }

                                                if (offsetResult.getSavedCropFile() != null) {
                                                        appendAutomaticFeederSetupLog("Slot "
                                                                        + measuredFeeder.getSlotAddress()
                                                                        + " Tentative "
                                                                        + offsetResult.getTentative()
                                                                        + ": Saved captured crop: "
                                                                        + offsetResult.getSavedCropFile());
                                                }
                                        }

                                        appendAutomaticFeederSetupLog(String.format(
                                                        "Slot %d XY corrections applied: %d.",
                                                        measuredFeeder.getSlotAddress(),
                                                        xyCorrectionResult.getCorrectionsApplied()));

                                        PhotonFeederAutomaticSetup.FeederZProbeResult zProbeResult = feederResult
                                                        .getZProbeResult();

                                        appendAutomaticFeederSetupLog(String.format(
                                                        "Slot %d Z probing: nozzle=%s, old Z=%.3f mm, contact Z=%.3f mm, release Z=%.3f mm, new Z=%.3f mm",
                                                        measuredFeeder.getSlotAddress(),
                                                        zProbeResult.getNozzleName(),
                                                        zProbeResult.getOldSlotZ(),
                                                        zProbeResult.getContactZ(),
                                                        zProbeResult.getReleaseZ(),
                                                        zProbeResult.getEstimatedSlotZ()));

                                        appendAutomaticFeederSetupLog(String.format(
                                                        "Slot %d Z probing: threshold=%.3f, contact reading=%.3f, release reading=%.3f, probe steps=%d, retract steps=%d",
                                                        measuredFeeder.getSlotAddress(),
                                                        zProbeResult.getThreshold(),
                                                        zProbeResult.getContactReading(),
                                                        zProbeResult.getReleaseReading(),
                                                        zProbeResult.getProbeSteps(),
                                                        zProbeResult.getRetractSteps()));

                                        appendAutomaticFeederSetupLog(String.format(
                                                        "Slot %d final location: X=%.3f Y=%.3f Z=%.3f",
                                                        measuredFeeder.getSlotAddress(),
                                                        feederResult.getFinalSlotLocation().getX(),
                                                        feederResult.getFinalSlotLocation().getY(),
                                                        feederResult.getFinalSlotLocation().getZ()));

                                        if (feederResult.isConfigurationSaved()) {
                                                appendAutomaticFeederSetupLog(String.format(
                                                                "Slot %d configuration saved.",
                                                                measuredFeeder.getSlotAddress()));
                                        }
                                }

                                if (allFeedersResult.isConfigurationSaved()) {
                                        appendAutomaticFeederSetupLog(
                                                        "Configuration saved after all successful feeder XY and Z corrections.");
                                }

                                appendAutomaticFeederSetupLog(
                                                "6.4.7 complete. All valid Photon feeders were corrected in XY and Z.");

                                appendAutomaticFeederSetupLog("No CSV rewrite was executed.");
                        }, (throwable) -> {
                                progressBarPanel.setVisible(false);
                                progressBarPanel.clearAllState();
                                setSearchControlsEnabled(true);

                                appendAutomaticFeederSetupLog(
                                                "Automatic feeder setup failed: " + throwable.getMessage());
                                MessageBoxes.errorBox(
                                                MainFrame.get(),
                                                "Automatic feeder setup error",
                                                throwable);
                        });

                }
        };

        private final Action eraseLogsAction = new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                        automaticFeederSetupLogTextArea.setText("");
                        appendAutomaticFeederSetupLog("Automatic feeder setup log erased.");
                }
        };
}