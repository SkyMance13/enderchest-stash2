package com.enderchest_stash;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

public class EnderChestStashClient implements ClientModInitializer {
    private static final int[] HOTBAR_INDICES_TO_STASH = {0, 1, 4, 7, 8};
    private static final int OFFHAND_INV_SLOT = 40;
    private static final int[] ARMOR_INV_SLOTS = {36, 37, 38, 39};
    private static final int HOTBAR_SCREEN_BASE = 54;
    public static KeyMapping stashAllKey;@Override
    public void onInitializeClient() {
        stashAllKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.enderchest_stash.stash_all", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, KeyMapping.Category.MISC));
        ClientTickEvents.END_CLIENT_TICK.register(client -> { while (stashAllKey.consumeClick()) { handleStashAll(client); } });
    }

    private void handleStashAll(Minecraft client) {
        if (client.player == null) return;
        AbstractContainerMenu h = getHandler(client);
        if (h == null) return;
        for (int hi : HOTBAR_INDICES_TO_STASH) { int s = HOTBAR_SCREEN_BASE + hi; if (s < h.slots.size() && !h.slots.get(s).getItem().isEmpty()) quickMove(client, h, s); }
        if (!client.player.getOffhandItem().isEmpty()) stashSpecial(client, h, OFFHAND_INV_SLOT);
        for (int a : ARMOR_INV_SLOTS) { if (!client.player.getInventory().getItem(a).isEmpty()) stashSpecial(client, h, a); }
    }

    private void stashSpecial(Minecraft client, AbstractContainerMenu h, int inv) {
        int s = freeSlot(h);
        if (s == -1) { client.player.displayClientMessage(Component.literal("No free hotbar slot!"), true); return; }
        client.gameMode.handleInventoryMouseClick(h.containerId, s, inv, ClickType.SWAP, client.player);
        quickMove(client, h, s);
        if (!h.slots.get(s).getItem().isEmpty()) { client.gameMode.handleInventoryMouseClick(h.containerId, s, inv, ClickType.SWAP, client.player); client.player.displayClientMessage(Component.literal("Chest is full!"), true); }
    }
private void quickMove(Minecraft client, AbstractContainerMenu h, int s) { client.gameMode.handleInventoryMouseClick(h.containerId, s, 0, ClickType.QUICK_MOVE, client.player); }

    private int freeSlot(AbstractContainerMenu h) { for (int i = 0; i < 9; i++) { int idx = HOTBAR_SCREEN_BASE + i; if (idx < h.slots.size() && h.slots.get(idx).getItem().isEmpty()) return idx; } return -1; }

    private AbstractContainerMenu getHandler(Minecraft client) {
        if (client.player == null) return null;
        AbstractContainerMenu m = client.player.containerMenu;
        if (m == client.player.inventoryMenu) { client.player.displayClientMessage(Component.literal("Open an ender chest first!"), true); return null; }
        if (m.slots.size() < 63) { client.player.displayClientMessage(Component.literal("Not an ender chest!"), true); return null; }
        return m;
    }
}
