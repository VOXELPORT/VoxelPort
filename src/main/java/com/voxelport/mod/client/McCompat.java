package com.voxelport.mod.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerTabOverlay;

/**
 * Small bridges over Minecraft 26.x API moves, so one jar runs on every 26.x release.
 * The game is unobfuscated from 26.1 on, so these names are the real ones and need no remapping.
 */
final class McCompat {
    private McCompat() {}

    /**
     * The player-list (tab) overlay. 26.1 has it on {@code Gui.getTabList()}; from 26.2 it moved to
     * {@code Gui.hud.getTabList()}. Returns null if neither exists, so the caller can skip the header.
     */
    static PlayerTabOverlay tabList(Minecraft mc) {
        Object gui = mc.gui;
        if (gui == null) return null;
        try {
            Object holder = gui;
            try {
                Field hud = gui.getClass().getField("hud");
                holder = hud.get(gui);
            } catch (NoSuchFieldException ignored) {
                // 26.1: the tab list is on Gui itself.
            }
            Method get = holder.getClass().getMethod("getTabList");
            return (PlayerTabOverlay) get.invoke(holder);
        } catch (ReflectiveOperationException | ClassCastException | NullPointerException e) {
            return null;
        }
    }
}
