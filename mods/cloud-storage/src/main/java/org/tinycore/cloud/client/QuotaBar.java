package org.tinycore.cloud.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.tinycore.cloud.cloud.CloudQuota;
import org.tinycore.cloud.menu.LinkQuota;
import org.tinycore.core.client.ui.ScreenStyle;
import org.tinycore.core.client.ui.UiFormat;

import java.util.ArrayList;
import java.util.List;

/**
 * Linha da cota no topo da tela do TC Cloud Link (só cliente): rótulo, barra de progresso e "usado / limite". A
 * barra mostra o limite mais apertado (tipos ou total, o que travar primeiro) e muda de cor perto do fim: normal,
 * aviso a partir de 75 %, perigo a partir de 95 %. O tooltip traz os dois números completos.
 */
final class QuotaBar {

    private static final int BAR_HEIGHT = 7;

    private QuotaBar() {}

    static void render(GuiGraphics g, Font font, LinkQuota quota, int x, int y, int width) {
        Component label = Component.translatable("gui.tccloud.quota");
        int labelWidth = font.width(label);
        g.drawString(font, label, x, y, ScreenStyle.TEXT_MUTED, false);
        String value = valueText(quota);
        int valueWidth = font.width(value);
        g.drawString(font, value, x + width - valueWidth, y, ScreenStyle.TEXT, false);

        int barX = x + labelWidth + 6;
        int barWidth = width - labelWidth - valueWidth - 12;
        ScreenStyle.inset(g, barX, y, barWidth, BAR_HEIGHT, ScreenStyle.SLOT);
        if (!quota.known() || unlimited(quota)) {
            return;
        }
        double fill = quota.fill();
        int filled = (int) Math.round((barWidth - 2) * fill);
        if (filled > 0) {
            g.fill(barX + 1, y + 1, barX + 1 + filled, y + BAR_HEIGHT - 1, color(fill));
        }
    }

    private static boolean unlimited(LinkQuota quota) {
        CloudQuota limits = quota.limits();
        return !limits.limitsTypes() && !limits.limitsTotal();
    }

    /** Texto curto à direita: total (ou tipos, se só eles têm limite); "—" sem sessão. */
    private static String valueText(LinkQuota quota) {
        if (!quota.known()) return "—";
        CloudQuota limits = quota.limits();
        if (limits.limitsTotal()) return UiFormat.compact(quota.total()) + " / " + UiFormat.compact(quota.maxTotal());
        if (limits.limitsTypes()) return quota.types() + " / " + quota.maxTypes();
        return Component.translatable("gui.tccloud.quota.unlimited").getString();
    }

    private static int color(double fill) {
        if (fill >= 0.95) return ScreenStyle.DANGER;
        if (fill >= 0.75) return ScreenStyle.WARNING;
        return ScreenStyle.ACCENT;
    }

    static List<Component> tooltip(LinkQuota quota) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.tccloud.quota.title"));
        if (!quota.known()) {
            lines.add(Component.translatable("gui.tccloud.quota.unknown").withStyle(ChatFormatting.GRAY));
            return lines;
        }
        CloudQuota limits = quota.limits();
        Component unlimited = Component.translatable("gui.tccloud.quota.unlimited");
        lines.add(Component.translatable("gui.tccloud.quota.total", String.format("%,d", quota.total()),
                limits.limitsTotal() ? Component.literal(String.format("%,d", quota.maxTotal())) : unlimited)
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("gui.tccloud.quota.types", quota.types(),
                limits.limitsTypes() ? Component.literal(String.valueOf(quota.maxTypes())) : unlimited)
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("gui.tccloud.quota.owner").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }
}
