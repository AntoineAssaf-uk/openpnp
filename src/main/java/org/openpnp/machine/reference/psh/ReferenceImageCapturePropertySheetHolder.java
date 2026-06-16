package org.openpnp.machine.reference.psh;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.io.File;

import javax.swing.Action;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.BorderFactory;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.JOptionPane;

import org.openpnp.machine.reference.capture.ReferenceImageCaptureService;
import org.openpnp.machine.reference.capture.ReferenceImageCaptureService.CaptureResult;
import org.openpnp.spi.PropertySheetHolder;
import org.openpnp.util.UiUtils;
import org.openpnp.machine.reference.debug.ReferenceMachineDebugLog;
import org.openpnp.machine.reference.imageoffset.CsImageOffsetResult;
import org.openpnp.machine.reference.imageoffset.ReferenceImageOffsetService;
import org.openpnp.machine.reference.lookup.ReferenceMachineLookup;
import org.openpnp.machine.reference.gantry.GantryTestCsvInput;
import org.openpnp.machine.reference.gantry.GantryTestCsvParser;
import org.openpnp.machine.reference.gantry.GantryTestDryRun;
import org.openpnp.machine.reference.gantry.GantryTestSingleCycleCapture;
import org.openpnp.machine.reference.gantry.GantryTestSingleCycleMove;
import org.openpnp.machine.reference.gantry.GantryTestSingleMove;
import org.openpnp.machine.reference.gantry.GantryTestSinglePointCapture;
import org.openpnp.machine.reference.gantry.GantryTestValidationResult;
import org.openpnp.machine.reference.gantry.GantryTestValidator;
import org.openpnp.machine.reference.gantry.GantryTestAllCyclesCapture;
import org.openpnp.machine.reference.gantry.GantryTestAllCyclesMove;

public class ReferenceImageCapturePropertySheetHolder implements PropertySheetHolder {
    private static final String TITLE = "Capture Reference Images";
    private static final int OPTIONAL_FOLDER_NAME_MAX_LENGTH = 32;

    @Override
    public String getPropertySheetHolderTitle() {
        return TITLE;
    }

    @Override
    public PropertySheetHolder[] getChildPropertySheetHolders() {
        return null;
    }

    @Override
    public PropertySheet[] getPropertySheets() {
        return new PropertySheet[] {
                new PropertySheet() {
                    @Override
                    public String getPropertySheetTitle() {
                        return TITLE;
                    }

                    @Override
                    public JPanel getPropertySheetPanel() {
                        return createPanel();
                    }
                }
        };
    }

    @Override
    public Action[] getPropertySheetHolderActions() {
        return null;
    }

    @Override
    public Icon getPropertySheetHolderIcon() {
        return null;
    }

    private JPanel createPanel() {
        JPanel panel = new JPanel();
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel savedOkLabel = new JLabel(" ");
        savedOkLabel.setForeground(new Color(0, 128, 0));
        savedOkLabel.setFont(savedOkLabel.getFont().deriveFont(Font.BOLD));
        savedOkLabel.setVisible(true);

        JTextArea debugTextArea = new JTextArea(14, 90);
        debugTextArea.setEditable(false);
        debugTextArea.setLineWrap(false);
        debugTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JScrollPane debugScrollPane = new JScrollPane(debugTextArea);
        debugScrollPane.setBorder(BorderFactory.createTitledBorder("Debug log"));
        debugScrollPane.setPreferredSize(new Dimension(760, 240));
        debugScrollPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, 240));

        JButton eraseLogsButton = new JButton("Erase logs");
        eraseLogsButton.addActionListener((ActionEvent e) -> {
            ReferenceMachineDebugLog.clear();
        });

        JButton lookupTestButton = new JButton("Lookup Test");

        lookupTestButton.setToolTipText("Test machine, head, camera, nozzle and actuator lookups for Gantry Test");

        lookupTestButton.addActionListener((ActionEvent e) -> {
            lookupTestButton.setEnabled(false);

            ReferenceMachineDebugLog.debugPrintf("Lookup Test pressed.");

            UiUtils.submitUiMachineTask(() -> {
                return ReferenceMachineLookup.describeStandardGantryObjects();
            }, (String result) -> {
                lookupTestButton.setEnabled(true);

                ReferenceMachineDebugLog.debugPrintln(result);
            }, (throwable) -> {
                lookupTestButton.setEnabled(true);

                ReferenceMachineDebugLog.debugException("Lookup Test failed", throwable);

                UiUtils.showError(throwable);
            });
        });

        JPanel eraseLogsButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));

        eraseLogsButtonPanel.add(eraseLogsButton);
        eraseLogsButtonPanel.add(Box.createHorizontalStrut(8));
        eraseLogsButtonPanel.add(lookupTestButton);

        ReferenceMachineDebugLog.setTextArea(debugTextArea);
        ReferenceMachineDebugLog.debugPrintf("Debug log panel ready.");

        JTextField optionalFolderNameTextField = new JTextField(32);
        Dimension optionalFolderNameTextFieldSize = optionalFolderNameTextField.getPreferredSize();
        optionalFolderNameTextField.setMinimumSize(optionalFolderNameTextFieldSize);
        optionalFolderNameTextField.setPreferredSize(optionalFolderNameTextFieldSize);
        optionalFolderNameTextField.setMaximumSize(optionalFolderNameTextFieldSize);

        ((AbstractDocument) optionalFolderNameTextField.getDocument())
                .setDocumentFilter(new MaxLengthDocumentFilter(OPTIONAL_FOLDER_NAME_MAX_LENGTH));

        JPanel optionalFolderNamePanel = new JPanel(new GridBagLayout());

        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = 0;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = new Insets(0, 0, 0, 8);

        optionalFolderNamePanel.add(new JLabel("Optional Folder Name"), labelConstraints);

        GridBagConstraints textFieldConstraints = new GridBagConstraints();
        textFieldConstraints.gridx = 1;
        textFieldConstraints.gridy = 0;
        textFieldConstraints.anchor = GridBagConstraints.WEST;
        textFieldConstraints.fill = GridBagConstraints.NONE;

        optionalFolderNamePanel.add(optionalFolderNameTextField, textFieldConstraints);

        GridBagConstraints messageConstraints = new GridBagConstraints();
        messageConstraints.gridx = 1;
        messageConstraints.gridy = 1;
        messageConstraints.anchor = GridBagConstraints.WEST;
        messageConstraints.insets = new Insets(4, 0, 0, 0);

        optionalFolderNamePanel.add(savedOkLabel, messageConstraints);

        GridBagConstraints fillerConstraints = new GridBagConstraints();
        fillerConstraints.gridx = 2;
        fillerConstraints.gridy = 0;
        fillerConstraints.weightx = 1.0;
        fillerConstraints.fill = GridBagConstraints.HORIZONTAL;

        optionalFolderNamePanel.add(Box.createHorizontalGlue(), fillerConstraints);

        Dimension optionalFolderNamePanelSize = optionalFolderNamePanel.getPreferredSize();
        optionalFolderNamePanel.setMinimumSize(optionalFolderNamePanelSize);
        optionalFolderNamePanel.setPreferredSize(optionalFolderNamePanelSize);
        optionalFolderNamePanel.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, optionalFolderNamePanelSize.height));

        JButton imageCaptureButton = new JButton("Image Capture");
        imageCaptureButton.setToolTipText("Image capture by camera");
        JPanel imageCaptureButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        imageCaptureButtonPanel.add(imageCaptureButton);

        Dimension imageCaptureButtonPanelSize = imageCaptureButtonPanel.getPreferredSize();
        imageCaptureButtonPanel.setMinimumSize(imageCaptureButtonPanelSize);
        imageCaptureButtonPanel.setPreferredSize(imageCaptureButtonPanelSize);
        imageCaptureButtonPanel.setMaximumSize(imageCaptureButtonPanelSize);

        imageCaptureButton.addActionListener((ActionEvent e) -> {
            savedOkLabel.setText(" ");
            imageCaptureButton.setEnabled(false);

            String optionalFolderName = optionalFolderNameTextField.getText();

            ReferenceMachineDebugLog.debugPrintf("Image Capture pressed.");
            ReferenceMachineDebugLog.debugPrintf("Optional Folder Name = \"%s\"", optionalFolderName);

            UiUtils.submitUiMachineTask(() -> {
                ReferenceMachineDebugLog.debugPrintf("Capture task started.");
                return ReferenceImageCaptureService.captureAndSaveReferenceImages(optionalFolderName);
            }, (CaptureResult result) -> {
                imageCaptureButton.setEnabled(true);

                optionalFolderNameTextField.setText("");

                savedOkLabel.setText("Data saved in folder " + result.getFolderName());

                savedOkLabel.setVisible(true);

                ReferenceMachineDebugLog.debugPrintf("Capture task completed.");
                ReferenceMachineDebugLog.debugPrintf("Data saved in folder %s", result.getFolderName());
                ReferenceMachineDebugLog.debugPrintf("Output path: %s", result.getFolder());

            }, (throwable) -> {
                imageCaptureButton.setEnabled(true);

                savedOkLabel.setText(" ");

                ReferenceMachineDebugLog.debugException("Capture task failed", throwable);

                UiUtils.showError(throwable);
            });
        });
        final File[] imageOffsetReferenceFile = new File[1];
        final File[] imageOffsetCapturedFile = new File[1];

        JTextField imageOffsetReferenceTextField = new JTextField(64);
        imageOffsetReferenceTextField.setEditable(false);

        JTextField imageOffsetCapturedTextField = new JTextField(64);
        imageOffsetCapturedTextField.setEditable(false);

        JButton imageOffsetReferenceBrowseButton = new JButton("Select Reference BMP");
        JButton imageOffsetCapturedBrowseButton = new JButton("Select Captured BMP");
        JButton imageOffsetTestButton = new JButton("Test Image Offset");

        imageOffsetReferenceBrowseButton.addActionListener((ActionEvent e) -> {
            File selectedFile = chooseBitmapFile(panel,
                    imageOffsetReferenceFile[0] == null ? null : imageOffsetReferenceFile[0].getParentFile());

            if (selectedFile != null) {
                imageOffsetReferenceFile[0] = selectedFile;
                imageOffsetReferenceTextField.setText(selectedFile.getAbsolutePath());
                ReferenceMachineDebugLog.debugPrintf("Image offset reference file selected: %s",
                        selectedFile.getAbsolutePath());
            }
        });

        imageOffsetCapturedBrowseButton.addActionListener((ActionEvent e) -> {
            File selectedFile = chooseBitmapFile(panel,
                    imageOffsetCapturedFile[0] == null ? null : imageOffsetCapturedFile[0].getParentFile());

            if (selectedFile != null) {
                imageOffsetCapturedFile[0] = selectedFile;
                imageOffsetCapturedTextField.setText(selectedFile.getAbsolutePath());
                ReferenceMachineDebugLog.debugPrintf("Image offset captured file selected: %s",
                        selectedFile.getAbsolutePath());
            }
        });

        imageOffsetTestButton.addActionListener((ActionEvent e) -> {
            if (imageOffsetReferenceFile[0] == null) {
                ReferenceMachineDebugLog.debugPrintf("Image offset test cancelled: no reference BMP selected.");
                return;
            }

            if (imageOffsetCapturedFile[0] == null) {
                ReferenceMachineDebugLog.debugPrintf("Image offset test cancelled: no captured BMP selected.");
                return;
            }

            imageOffsetTestButton.setEnabled(false);

            ReferenceMachineDebugLog.debugPrintf("Image offset test started.");
            ReferenceMachineDebugLog.debugPrintf("Reference BMP: %s", imageOffsetReferenceFile[0].getAbsolutePath());
            ReferenceMachineDebugLog.debugPrintf("Captured BMP:  %s", imageOffsetCapturedFile[0].getAbsolutePath());

            UiUtils.submitUiMachineTask(() -> {
                return ReferenceImageOffsetService.findOffset(imageOffsetReferenceFile[0], imageOffsetCapturedFile[0]);
            }, (CsImageOffsetResult result) -> {
                imageOffsetTestButton.setEnabled(true);

                ReferenceMachineDebugLog.debugPrintf("Image offset test completed.");
                ReferenceMachineDebugLog.debugPrintf("dx = %.6f pixels", result.getDx());
                ReferenceMachineDebugLog.debugPrintf("dy = %.6f pixels", result.getDy());
                ReferenceMachineDebugLog.debugPrintf("peak = %.9f", result.getPeak());
                ReferenceMachineDebugLog.debugPrintf("dt = %d ms", result.getDt());
            }, (throwable) -> {
                imageOffsetTestButton.setEnabled(true);

                ReferenceMachineDebugLog.debugException("Image offset test failed", throwable);

                UiUtils.showError(throwable);
            });
        });

        JPanel imageOffsetTestPanel = new JPanel(new GridBagLayout());
        imageOffsetTestPanel.setBorder(BorderFactory.createTitledBorder("Image Offset Test"));

        GridBagConstraints offsetLabelConstraints = new GridBagConstraints();
        offsetLabelConstraints.gridx = 0;
        offsetLabelConstraints.gridy = 0;
        offsetLabelConstraints.anchor = GridBagConstraints.WEST;
        offsetLabelConstraints.insets = new Insets(0, 0, 4, 8);
        imageOffsetTestPanel.add(new JLabel("Reference BMP"), offsetLabelConstraints);

        GridBagConstraints offsetReferenceTextConstraints = new GridBagConstraints();
        offsetReferenceTextConstraints.gridx = 1;
        offsetReferenceTextConstraints.gridy = 0;
        offsetReferenceTextConstraints.weightx = 1.0;
        offsetReferenceTextConstraints.fill = GridBagConstraints.HORIZONTAL;
        offsetReferenceTextConstraints.insets = new Insets(0, 0, 4, 8);
        imageOffsetTestPanel.add(imageOffsetReferenceTextField, offsetReferenceTextConstraints);

        GridBagConstraints offsetReferenceButtonConstraints = new GridBagConstraints();
        offsetReferenceButtonConstraints.gridx = 2;
        offsetReferenceButtonConstraints.gridy = 0;
        offsetReferenceButtonConstraints.anchor = GridBagConstraints.WEST;
        offsetReferenceButtonConstraints.insets = new Insets(0, 0, 4, 0);
        imageOffsetTestPanel.add(imageOffsetReferenceBrowseButton, offsetReferenceButtonConstraints);

        GridBagConstraints offsetCapturedLabelConstraints = new GridBagConstraints();
        offsetCapturedLabelConstraints.gridx = 0;
        offsetCapturedLabelConstraints.gridy = 1;
        offsetCapturedLabelConstraints.anchor = GridBagConstraints.WEST;
        offsetCapturedLabelConstraints.insets = new Insets(0, 0, 4, 8);
        imageOffsetTestPanel.add(new JLabel("Captured BMP"), offsetCapturedLabelConstraints);

        GridBagConstraints offsetCapturedTextConstraints = new GridBagConstraints();
        offsetCapturedTextConstraints.gridx = 1;
        offsetCapturedTextConstraints.gridy = 1;
        offsetCapturedTextConstraints.weightx = 1.0;
        offsetCapturedTextConstraints.fill = GridBagConstraints.HORIZONTAL;
        offsetCapturedTextConstraints.insets = new Insets(0, 0, 4, 8);
        imageOffsetTestPanel.add(imageOffsetCapturedTextField, offsetCapturedTextConstraints);

        GridBagConstraints offsetCapturedButtonConstraints = new GridBagConstraints();
        offsetCapturedButtonConstraints.gridx = 2;
        offsetCapturedButtonConstraints.gridy = 1;
        offsetCapturedButtonConstraints.anchor = GridBagConstraints.WEST;
        offsetCapturedButtonConstraints.insets = new Insets(0, 0, 4, 0);
        imageOffsetTestPanel.add(imageOffsetCapturedBrowseButton, offsetCapturedButtonConstraints);

        GridBagConstraints offsetTestButtonConstraints = new GridBagConstraints();
        offsetTestButtonConstraints.gridx = 1;
        offsetTestButtonConstraints.gridy = 2;
        offsetTestButtonConstraints.anchor = GridBagConstraints.WEST;
        offsetTestButtonConstraints.insets = new Insets(4, 0, 0, 0);
        imageOffsetTestPanel.add(imageOffsetTestButton, offsetTestButtonConstraints);

        final File[] gantryTestInputFile = new File[1];

        JTextField gantryTestInputTextField = new JTextField(64);

        gantryTestInputTextField.setEditable(false);

        JButton gantryTestInputBrowseButton = new JButton("Select Gantry CSV");
        JButton gantryTestCaptureAllCyclesButton = new JButton("Capture All Cycles");

        gantryTestInputBrowseButton.addActionListener((ActionEvent e) -> {
            File selectedFile = chooseCsvFile(panel,
                    gantryTestInputFile[0] == null ? null : gantryTestInputFile[0].getParentFile());

            if (selectedFile != null) {
                gantryTestInputFile[0] = selectedFile;
                gantryTestInputTextField.setText(selectedFile.getAbsolutePath());

                ReferenceMachineDebugLog.debugPrintf("Gantry Test CSV selected: %s", selectedFile.getAbsolutePath());
            }
        });

        gantryTestCaptureAllCyclesButton.addActionListener((ActionEvent e) -> {
            if (gantryTestInputFile[0] == null) {
                ReferenceMachineDebugLog.debugPrintf("Gantry Test capture all cycles cancelled: no CSV selected.");
                return;
            }

            int confirmation = JOptionPane.showConfirmDialog(
                    panel,
                    "This will move the real machine through ALL Gantry Test CSV points\n"
                            + "for ALL cycles specified in the CSV and capture one Top or Bottom camera image\n"
                            + "at every point visit.\n\n"
                            + "Original BMP, mono BMP and mono crop BMP will be saved for every visit.\n"
                            + "No image offset calculation will be performed yet.\n\n"
                            + "For your current 10-cycle / 3-point CSV this means 30 captures and 90 BMP files.\n\n"
                            + "Make sure the machine is clear and you are ready to stop it if needed.\n\n"
                            + "Continue?",
                    "Confirm Gantry Test All Cycles Capture",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);

            if (confirmation != JOptionPane.YES_OPTION) {
                ReferenceMachineDebugLog.debugPrintf("Gantry Test capture all cycles cancelled by user.");
                return;
            }

            gantryTestCaptureAllCyclesButton.setEnabled(false);

            ReferenceMachineDebugLog.debugPrintf("Gantry Test capture all cycles started.");
            ReferenceMachineDebugLog.debugPrintf("Input CSV: %s", gantryTestInputFile[0].getAbsolutePath());

            UiUtils.submitUiMachineTask(() -> {
                GantryTestCsvInput input = GantryTestCsvParser.read(gantryTestInputFile[0].toPath());
                GantryTestValidationResult validationResult = GantryTestValidator.validate(input);

                if (!validationResult.isPassed()) {
                    return validationResult.describe()
                            + System.lineSeparator()
                            + "Gantry Test capture all cycles CANCELLED because validation failed.";
                }

                return validationResult.describe()
                        + System.lineSeparator()
                        + System.lineSeparator()
                        + GantryTestAllCyclesCapture.moveAndCaptureAllCycles(input);
            }, (String result) -> {
                gantryTestCaptureAllCyclesButton.setEnabled(true);

                ReferenceMachineDebugLog.debugPrintln(result);
            }, (throwable) -> {
                gantryTestCaptureAllCyclesButton.setEnabled(true);

                ReferenceMachineDebugLog.debugException("Gantry Test capture all cycles failed", throwable);

                UiUtils.showError(throwable);
            });
        });

        JPanel gantryTestPanel = new JPanel(new GridBagLayout());
        gantryTestPanel.setBorder(BorderFactory.createTitledBorder("Gantry Test"));

        GridBagConstraints gantryCsvLabelConstraints = new GridBagConstraints();
        gantryCsvLabelConstraints.gridx = 0;
        gantryCsvLabelConstraints.gridy = 0;
        gantryCsvLabelConstraints.anchor = GridBagConstraints.WEST;
        gantryCsvLabelConstraints.insets = new Insets(0, 0, 4, 8);
        gantryTestPanel.add(new JLabel("Input CSV"), gantryCsvLabelConstraints);

        GridBagConstraints gantryCsvTextConstraints = new GridBagConstraints();
        gantryCsvTextConstraints.gridx = 1;
        gantryCsvTextConstraints.gridy = 0;
        gantryCsvTextConstraints.weightx = 1.0;
        gantryCsvTextConstraints.fill = GridBagConstraints.HORIZONTAL;
        gantryCsvTextConstraints.insets = new Insets(0, 0, 4, 8);
        gantryTestPanel.add(gantryTestInputTextField, gantryCsvTextConstraints);

        JPanel gantryButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));

        gantryButtonPanel.add(gantryTestInputBrowseButton);
        gantryButtonPanel.add(Box.createHorizontalStrut(8));
        gantryButtonPanel.add(gantryTestCaptureAllCyclesButton);

        GridBagConstraints gantryButtonPanelConstraints = new GridBagConstraints();

        gantryButtonPanelConstraints.gridx = 1;
        gantryButtonPanelConstraints.gridy = 1;
        gantryButtonPanelConstraints.anchor = GridBagConstraints.WEST;
        gantryButtonPanelConstraints.insets = new Insets(4, 0, 0, 0);

        gantryTestPanel.add(gantryButtonPanel, gantryButtonPanelConstraints);

        panel.add(imageCaptureButtonPanel);
        panel.add(Box.createVerticalStrut(8));
        panel.add(optionalFolderNamePanel);
        panel.add(Box.createVerticalStrut(14));
        panel.add(imageOffsetTestPanel);
        panel.add(Box.createVerticalStrut(14));
        panel.add(gantryTestPanel);
        panel.add(Box.createVerticalStrut(28));
        panel.add(debugScrollPane);
        panel.add(Box.createVerticalStrut(8));
        panel.add(eraseLogsButtonPanel);

        panel.addAncestorListener(new AncestorListener() {
            @Override
            public void ancestorAdded(AncestorEvent event) {
                optionalFolderNameTextField.setText("");
                savedOkLabel.setText(" ");
            }

            @Override
            public void ancestorRemoved(AncestorEvent event) {
            }

            @Override
            public void ancestorMoved(AncestorEvent event) {
            }
        });

        imageCaptureButtonPanel.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        optionalFolderNamePanel.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        imageOffsetTestPanel.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        gantryTestPanel.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        debugScrollPane.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        eraseLogsButtonPanel.setAlignmentX(JPanel.LEFT_ALIGNMENT);

        return panel;
    }

    private static File chooseCsvFile(JPanel parent, File initialDirectory) {
        JFileChooser fileChooser = new JFileChooser();

        fileChooser.setDialogTitle("Select Gantry Test CSV");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setFileFilter(new FileNameExtensionFilter("CSV Files (*.csv)", "csv"));

        if (initialDirectory != null && initialDirectory.exists()) {
            fileChooser.setCurrentDirectory(initialDirectory);
        } else {
            File defaultDirectory = new File("C:\\Opulo\\Data");

            if (defaultDirectory.exists()) {
                fileChooser.setCurrentDirectory(defaultDirectory);
            }
        }

        int result = fileChooser.showOpenDialog(parent);

        if (result == JFileChooser.APPROVE_OPTION) {
            return fileChooser.getSelectedFile();
        }

        return null;
    }

    private static File chooseBitmapFile(JPanel parent, File initialDirectory) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select Bitmap image");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setFileFilter(new FileNameExtensionFilter("Bitmap Images (*.bmp)", "bmp"));

        if (initialDirectory != null && initialDirectory.exists()) {
            fileChooser.setCurrentDirectory(initialDirectory);
        } else {
            File defaultDirectory = new File("C:\\Opulo\\Data");
            if (defaultDirectory.exists()) {
                fileChooser.setCurrentDirectory(defaultDirectory);
            }
        }

        int result = fileChooser.showOpenDialog(parent);
        if (result == JFileChooser.APPROVE_OPTION) {
            return fileChooser.getSelectedFile();
        }

        return null;
    }

    private static class MaxLengthDocumentFilter extends DocumentFilter {
        private final int maxLength;

        private MaxLengthDocumentFilter(int maxLength) {
            this.maxLength = maxLength;
        }

        @Override
        public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
                throws BadLocationException {
            if (string == null) {
                return;
            }

            int currentLength = fb.getDocument().getLength();
            int insertLength = Math.min(string.length(), maxLength - currentLength);

            if (insertLength > 0) {
                super.insertString(fb, offset, string.substring(0, insertLength), attr);
            }
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException {
            if (text == null) {
                super.replace(fb, offset, length, text, attrs);
                return;
            }

            int currentLength = fb.getDocument().getLength();
            int newLengthWithoutInsertedText = currentLength - length;
            int allowedInsertLength = maxLength - newLengthWithoutInsertedText;

            if (allowedInsertLength <= 0) {
                super.replace(fb, offset, length, "", attrs);
                return;
            }

            String insertedText = text.substring(0, Math.min(text.length(), allowedInsertLength));
            super.replace(fb, offset, length, insertedText, attrs);
        }
    }
}