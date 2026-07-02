package org.openpnp.machine.photon.sheets.gui;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Locale;

import org.openpnp.machine.photon.PhotonFeederSlots.Slot;
import org.openpnp.model.AbstractModelObject;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;

public class SlotProxy extends AbstractModelObject {
    private Slot slot;

    private final PropertyChangeListener slotLocationListener =
            this::slotLocationChanged;

    public void setSlot(Slot slot) {
        Slot oldSlot = this.slot;
        boolean oldIsEnabled = isEnabled();
        String oldSlotAddress = getSlotAddress();
        Location oldLocation = getLocation();

        if (this.slot != null) {
            this.slot.removePropertyChangeListener("location", slotLocationListener);
        }

        this.slot = slot;

        if (this.slot != null) {
            this.slot.addPropertyChangeListener("location", slotLocationListener);
        }

        firePropertyChange("slot", oldSlot, slot);
        firePropertyChange("enabled", oldIsEnabled, isEnabled());
        firePropertyChange("slotAddress", oldSlotAddress, getSlotAddress());
        firePropertyChange("location", oldLocation, getLocation());
    }

    public boolean isEnabled() {
        return slot != null;
    }

    public String getSlotAddress() {
        if (slot == null) {
            return "None";
        }
        else {
            return String.format(Locale.US, "%d", slot.getAddress());
        }
    }

    public Location getLocation() {
        if (slot == null) {
            return new Location(LengthUnit.Millimeters);
        }
        else {
            return slot.getLocation();
        }
    }

    public void setLocation(Location location) {
        Location oldLocation = getLocation();

        if (slot != null) {
            slot.setLocation(location);
        }

        firePropertyChange("location", oldLocation, getLocation());
    }

    private void slotLocationChanged(PropertyChangeEvent event) {
        firePropertyChange("location", event.getOldValue(), event.getNewValue());
    }
}