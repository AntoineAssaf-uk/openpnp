package org.openpnp.machine.reference.psh;

import java.util.Arrays;

import javax.swing.Action;
import javax.swing.Icon;

import org.openpnp.spi.PropertySheetHolder;

public class ReferencePositionsPropertySheetHolder implements PropertySheetHolder {
    @Override
    public String getPropertySheetHolderTitle() {
        return "Reference Positions";
    }

    @Override
    public PropertySheetHolder[] getChildPropertySheetHolders() {
        return Arrays.asList(
                new ReferenceImageCapturePropertySheetHolder()
        ).toArray(new PropertySheetHolder[] {});
    }

    @Override
    public PropertySheet[] getPropertySheets() {
        return null;
    }

    @Override
    public Action[] getPropertySheetHolderActions() {
        return null;
    }

    @Override
    public Icon getPropertySheetHolderIcon() {
        return null;
    }
}