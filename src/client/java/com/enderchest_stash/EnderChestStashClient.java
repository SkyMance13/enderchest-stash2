package com.enderchest_stash;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
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
        stashAllKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.enderchest_stash.stash_all",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                net.minecraft.client.KeyMapping.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (stashAllKey.isDown()) {
                handleStashAll(client);
            }
        });
    }

    private void handleStashAll(Minecraft client) {
        if (client.player == null) return;
        AbstractContainerMenu handler = getEnderChestHandler(client);
        if (handler == null) return;

        for (int hi : HOTBAR_INDICES_TO_STASH) {
            int screenSlot = HOTBAR_SCREEN_BASE + hi;
            if (screenSlot < handler.slots.size() && !handler.slots.get(screenSlot).getItem().isEmpty()) {
                quickMove(client, handler, screenSlot);
            }
        }

        if (!client.player.getOffhandItem().isEmpty()) {
            stashSpecialSlot(client, handler, OFFHAND_INV_SLOT);
        }

        for (int armorSlot : ARMOR_INV_SLOTS) {
            ItemStack armorStack = client.player.getInventory().getItem(armorSlot);
            if (!armorStack.isEmpty()) {
                stashSpecialSlot(client, handler, armorSlot);
            }
        }
    }private void stashSpecialSlot(Minecraft client, AbstractContainerMenu handler, int playerInvSlotIndex) {
        int stagingScreenSlot = findFreeHotbarScreenSlot(handler);
        if (stagingScreenSlot == -1) {
            client.player.displayClientMessage(Component.literal("§eEnderChest Stash: No free hotbar slot!"), true);
            return;
        }
        client.gameMode.handleInventoryMouseClick(
                handler.containerId, stagingScreenSlot, playerInvSlotIndex,
                net.minecraft.world.inventory.ClickType.SWAP, client.player);
        quickMove(client, handler, stagingScreenSlot);
        if (!handler.slots.get(stagingScreenSlot).getItem().isEmpty()) {
            client.gameMode.handleInventoryMouseClick(
                    handler.containerId, stagingScreenSlot, playerInvSlotIndex,
                    net.minecraft.world.inventory.ClickType.SWAP, client.player);
            client.player.displayClientMessage(Component.literal("§eEnderChest Stash: Chest is full!"), true);
        }
    }

    private void quickMove(Minecraft client, AbstractContainerMenu handler, int screenSlot) {
        client.gameMode.handleInventoryMouseClick(
                handler.containerId, screenSlot, 0,
                net.minecraft.world.inventory.ClickType.QUICK_MOVE, client.player);
    }private int findFreeHotbarScreenSlot(AbstractContainerMenu handler) {
        for (int i = 0; i < 9; i++) {
            int idx = HOTBAR_SCREEN_BASE + i;
            if (idx < handler.slots.size() && handler.slots.get(idx).getItem().isEmpty()) {
                return idx;
            }
        }
        return -1;
    }

    private AbstractContainerMenu getEnderChestHandler(Minecraft client) {
        if (client.player == null) return null;
        AbstractContainerMenu menu = client.player.containerMenu;
        if (menu == client.player.inventoryMenu) {
            client.player.displayClientMessage(Component.literal("§cEnderChest Stash: Open an ender chest first!"), true);
            return null;
        }
        if (menu.slots.size() < 63) {
            client.player.displayClientMessage(Component.literal("§cEnderChest Stash: This doesn't look like an ender chest!"), true);
            return null;
        }
        return menu;
    }
}
    
