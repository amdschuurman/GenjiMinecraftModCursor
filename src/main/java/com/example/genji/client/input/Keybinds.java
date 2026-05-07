package com.example.genji.client.input;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Central registry for Genji key mappings.
 * ClientInit should call Keybinds.register(event) during RegisterKeyMappingsEvent.
 */
public final class Keybinds {
    private Keybinds() {}

    public static final String CATEGORY = "key.categories.genji"; // shown as "Genji" via lang

    /** Dragonblade (Ultimate) */
    public static KeyMapping BLADE;

    /** Nano-Boost */
    public static KeyMapping NANO;

    /** Deflect */
    public static KeyMapping DEFLECT;

    /** Dash */
    public static KeyMapping DASH;

    /** Invoked by ClientInit during the MOD bus RegisterKeyMappingsEvent */
    public static void register(RegisterKeyMappingsEvent e) {
        BLADE   = new KeyMapping("key.genji.blade",   GLFW.GLFW_KEY_V,        CATEGORY);
        NANO    = new KeyMapping("key.genji.nano",    GLFW.GLFW_KEY_B,        CATEGORY);
        DEFLECT = new KeyMapping("key.genji.deflect", GLFW.GLFW_KEY_Q,        CATEGORY);
        DASH    = new KeyMapping("key.genji.dash",    GLFW.GLFW_KEY_LEFT_ALT, CATEGORY);

        e.register(BLADE);
        e.register(NANO);
        e.register(DEFLECT);
        e.register(DASH);
    }
}
