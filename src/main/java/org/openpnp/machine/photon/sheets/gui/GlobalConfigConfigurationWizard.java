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
                return PhotonFeederAutomaticSetup.searchAndCollectValidFeeders(
                        progressBarPanel::updateFeederState);
            }, (searchResult) -> {
                progressBarPanel.setVisible(false);
                progressBarPanel.clearAllState();
                setSearchControlsEnabled(true);

                appendAutomaticFeederSetupLog("Photon feeder search completed.");
                appendAutomaticFeederSetupLog(String.format(
                        "Photon feeders listed: %d, valid for automatic setup: %d, invalid/skipped: %d.",
                        searchResult.getTotalCount(),
                        searchResult.getValidCount(),
                        searchResult.getInvalidCount()));

                for (PhotonFeederAutomaticSetup.FeederSummary feederSummary : searchResult.getFeederSummaries()) {
                    if (feederSummary.isValid()) {
                        appendAutomaticFeederSetupLog(String.format(
                                "VALID  Slot %d  %s  Location X=%.3f Y=%.3f Z=%.3f",
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
                                        : feederSummary.getSlotAddress().toString(),
                                feederSummary.getDisplayName(),
                                feederSummary.getInvalidReason()));
                    }
                }

                appendAutomaticFeederSetupLog(
                        "6.4.3 complete. No machine motion or feeder update was executed.");
            }, (throwable) -> {
                progressBarPanel.setVisible(false);
                progressBarPanel.clearAllState();
                setSearchControlsEnabled(true);

                appendAutomaticFeederSetupLog(
                        "Photon feeder search failed: " + throwable.getMessage());
                MessageBoxes.errorBox(
                        MainFrame.get(),
                        "Automatic feeder setup search error",
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