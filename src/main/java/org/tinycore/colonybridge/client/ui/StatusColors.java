package org.tinycore.colonybridge.client.ui;

import org.tinycore.colonybridge.logic.BridgeStatus;

/** Cor de cada estado da ponte, igual na tela da ponte e nos monitores. */
public final class StatusColors {

    private StatusColors() {}

    public static int of(BridgeStatus status) {
        return switch (status) {
            case WORKING -> UiColors.HIGHLIGHT;
            case IDLE -> UiColors.SUCCESS;
            case STARTING, PAUSED -> UiColors.WARNING;
            case OFFLINE, INVALID_CABLE -> UiColors.TEXT_MUTED;
            case NO_COLONY, NO_PERMISSION, NO_WAREHOUSE -> UiColors.DANGER;
        };
    }
}
