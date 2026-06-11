package org.openpnp.machine.reference.psh;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;

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

import org.openpnp.machine.reference.capture.ReferenceImageCaptureService;
import org.openpnp.machine.reference.capture.ReferenceImageCaptureService.CaptureResult;
import org.openpnp.spi.PropertySheetHolder;
import org.openpnp.util.UiUtils;
import org.openpnp.machine.reference.debug.ReferenceMachineDebugLog;

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

        JPanel eraseLogsButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        eraseLogsButtonPanel.add(eraseLogsButton);

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
        panel.add(imageCaptureButtonPanel);
        panel.add(Box.createVerticalStrut(28));
        panel.add(optionalFolderNamePanel);
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
        debugScrollPane.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        eraseLogsButtonPanel.setAlignmentX(JPanel.LEFT_ALIGNMENT);

        return panel;
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