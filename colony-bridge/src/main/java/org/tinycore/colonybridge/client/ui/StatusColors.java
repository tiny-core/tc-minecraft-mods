package org.tinycore.colonybridge.client.ui;

import org.tinycore.colonybridge.logic.BridgeStatus;
import org.tinycore.colonybridge.logic.bridge.RequestOutcome;
import org.tinycore.core.client.ui.UiColors;

/** Cores de estado da ponte e de resultado de pedido, iguais na tela da ponte e nos monitores. */
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

    public static int of(RequestOutcome outcome) {
        return switch (outcome) {
            case DELIVERED, WAITING_COURIER, IN_WAREHOUSE -> UiColors.SUCCESS;
            case CRAFT_STARTED, CRAFTING -> UiColors.HIGHLIGHT;
            case QUEUED, OTHER_BRIDGE -> UiColors.TEXT_MUTED;
            case RACKS_FULL, CRAFTING_DISABLED, FILTERED -> UiColors.WARNING;
            case NO_STOCK, NOT_CRAFTABLE -> UiColors.DANGER;
        };
    }
}
