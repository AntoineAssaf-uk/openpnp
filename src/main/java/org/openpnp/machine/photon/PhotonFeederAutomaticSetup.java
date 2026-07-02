package org.openpnp.machine.photon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.openpnp.model.Configuration;
import org.openpnp.model.Location;
import org.openpnp.spi.Feeder;

public class PhotonFeederAutomaticSetup {
    private PhotonFeederAutomaticSetup() {
    }

    public static SearchResult searchAndCollectValidFeeders(
            PhotonFeeder.FeederSearchProgressConsumer progressUpdate) throws Exception {
        PhotonFeeder.findAllFeeders(progressUpdate);
        return collectValidFeeders();
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