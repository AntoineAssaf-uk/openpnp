package org.openpnp.machine.reference.psh;

import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import org.openpnp.spi.PropertySheetHolder;

public class ReferenceImageCapturePropertySheetHolder implements PropertySheetHolder {
    private static final String TITLE = "Capture Reference Images";

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

        JLabel savedOkLabel = new JLabel("Image Saved Ok..");
        savedOkLabel.setForeground(new Color(0, 128, 0));
        savedOkLabel.setFont(savedOkLabel.getFont().deriveFont(Font.BOLD));
        savedOkLabel.setVisible(false);

        JButton imageCaptureButton = new JButton(new AbstractAction("Image Capture") {
            @Override
            public void actionPerformed(ActionEvent e) {
                savedOkLabel.setVisible(false);

                // Temporary UI test only.
                // Real camera capture will be connected in the next step.
                savedOkLabel.setVisible(true);
            }
        });
        imageCaptureButton.setToolTipText("Image capture by camera");

        panel.add(imageCaptureButton);
        panel.add(Box.createVerticalStrut(12));
        panel.add(savedOkLabel);

        return panel;
    }
}