package com.example.genji.client.hud;

import com.example.genji.client.ClientGenjiData;
import com.example.genji.config.GenjiConfig;
import com.example.genji.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/**
 * Right-side stacked ability HUD (Nano, Blade, Deflect, Dash).
 * - Unified bar color across abilities EXCEPT Nano which stays blue.
 * - Times shown as "Xs" (no "CD").
 * - Dragonblade status strings ("Unsheathing", "Sheathing") don't overlap other rows.
 */
public final class AbilityHud {
    private AbilityHud() {}

    private static final int BOX_BG = 0xAA101010;
    private static final int TEXT   = 0xFFFFFFFF;

    private static final int ABILITY_BAR = 0x88FFFFFF; // unified for Blade/Deflect/Dash
    private static final int NANO_BAR    = 0x884090FF; // keep Nano blue

    public static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.isPaused()) return;

        ItemStack main = mc.player.getMainHandItem();
        ItemStack off  = mc.player.getOffhandItem();
        boolean holdingGenji =
                (!main.isEmpty() && (main.is(ModItems.SHURIKEN.get()) || main.is(ModItems.DRAGONBLADE.get()))) ||
                        (!off.isEmpty()  && (off.is(ModItems.SHURIKEN.get())  || off.is(ModItems.DRAGONBLADE.get())));
        if (!holdingGenji) return;

        int screenW = g.guiWidth();
        int pad = 4;
        int w = 120, h = 18;
        int x = screenW - w - 8;
        int y = 8;

        // --- Nano-Boost (first row) ---
        drawBarBox(
                g, x, y, w, h,
                "Nano-Boost",
                ClientGenjiData.nano >= 100 ? "READY" : (ClientGenjiData.nano + "%"),
                ClientGenjiData.nano, 100, NANO_BAR
        );
        y += h + pad;

        // --- Dragonblade ---
        drawBarBox(
                g, x, y, w, h,
                "Dragonblade",
                ClientGenjiData.ult >= 100 ? "READY" : (ClientGenjiData.ult + "%"),
                ClientGenjiData.ult, 100, ABILITY_BAR
        );
        y += h + pad;

        // --- Deflect ---
        String deflectRight;
        int deflectFillNow, deflectFillTotal;
        int deflectDurationTotal = Math.max(1, GenjiConfig.secToTicksClamped(GenjiConfig.DEFLECT_MAX_DURATION_SECONDS));
        int deflectCooldownTotal = Math.max(1, GenjiConfig.secToTicksClamped(GenjiConfig.DEFLECT_COOLDOWN_SECONDS));
        if (ClientGenjiData.deflectTicks > 0) {
            deflectRight   = fmtTicks(ClientGenjiData.deflectTicks);
            deflectFillTotal = deflectDurationTotal;
            deflectFillNow = Math.min(deflectFillTotal, ClientGenjiData.deflectTicks);
        } else if (ClientGenjiData.deflectCooldown > 0) {
            deflectRight   = fmtTicks(ClientGenjiData.deflectCooldown);
            deflectFillTotal = deflectCooldownTotal;
            deflectFillNow = Math.min(deflectFillTotal, ClientGenjiData.deflectCooldown);
        } else {
            deflectRight = "READY";
            deflectFillNow = 0;
            deflectFillTotal = 1;
        }
        drawBarBox(g, x, y, w, h, "Deflect", deflectRight, deflectFillNow, deflectFillTotal, ABILITY_BAR);
        y += h + pad;

        // --- Dash ---
        String dashRight = ClientGenjiData.dashCooldown > 0 ? fmtTicks(ClientGenjiData.dashCooldown) : "READY";
        int dashCooldownTotal = Math.max(1, GenjiConfig.secToTicksClamped(GenjiConfig.DASH_COOLDOWN_SECONDS));
        drawBarBox(g, x, y, w, h, "Dash",
                dashRight,
                ClientGenjiData.dashCooldown,
                dashCooldownTotal,
                ABILITY_BAR
        );
    }


    private static void drawBarBox(GuiGraphics g, int x, int y, int w, int h,
                                   String label, String rightText,
                                   int amount, int total, int argbFill) {
        Minecraft mc = Minecraft.getInstance();
        g.fill(x, y, x + w, y + h, BOX_BG);

        int inner = w - 4;
        int filled = total <= 0 ? 0 : Math.round((amount / (float) total) * inner);
        if (argbFill != 0 && filled > 0) {
            filled = Math.max(1, Math.min(filled, inner));
            g.fill(x + 2, y + h - 4, x + 2 + filled, y + h - 2, argbFill);
        }

        g.drawString(mc.font, label, x + 6, y + 5, TEXT, false);
        int rx = x + w - 6 - mc.font.width(rightText);
        g.drawString(mc.font, rightText, rx, y + 5, TEXT, false);
    }


    /** Format ticks as "Xs" with up to one decimal; 20t = 1s. */
    private static String fmtTicks(int ticks) {
        if (ticks <= 0) return "0s";
        float s = ticks / 20f;
        float rounded = Math.round(s * 10f) / 10f;
        // Show integer seconds without decimal, otherwise one decimal.
        if (Math.abs(rounded - Math.round(rounded)) < 0.05f) {
            return ((int)Math.round(rounded)) + "s";
        }
        return String.format("%.1fs", rounded);
    }
}
