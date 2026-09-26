package org.tinycore.colonybridge.logic;

public enum BridgeStatus {
    STARTING,
    OFFLINE,
    NO_COLONY,
    NO_WAREHOUSE,
    IDLE,
    WORKING;

    public String translationKey() {
        return "status.tccolonybridge." + name().toLowerCase();
    }
}
