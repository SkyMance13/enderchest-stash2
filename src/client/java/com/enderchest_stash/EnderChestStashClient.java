package com.enderchest_stash;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * EnderChest Stash Mod — Fabric 1.21.1
 *
 * Keybind H  (default) — Stash hotbar slots 1,2,5,8,9 + offhand + all armor into open ender chest
 *
 * ── Ender Chest Screen Handler slot layout ──────────────────────────────────
 *  Ender chest inventory : slots  0 –  26  (3 rows × 9)
 *  Player main inventory : slots 27 –  53  (3 rows × 9, top row first)
 *  Player hotbar         : slots 54 –  62  (left to right, hotbar 0–8)
 *
 *  Armor (36–39) and offhand (40) are NOT exposed as screen slots in the
 *  ender chest screen handler. We move them via SlotActionType.SWAP:
 *    clickSlot(syncId, screenSlot, button, SWAP, player)
 *  where `button` is the player inventory slot index to swap with.
 *  button 40 = offhand, button 36–39 = armor.
 * ────────────────────────────────────────────────────────────────────────────
 */
public class EnderChestStashClient implements ClientModInitializer {

    /** Hotbar indices (0-based) to stash: slots 1,2,5,8,9 -> indices 0,1,4,7,8 */
    private static final int[] HOTBAR_INDICES_TO_STASH = {0, 1, 4, 7, 8};

    /**
     * Player inventory indices for armor and offhand (used as SWAP button values):
     *   36 = boots, 37 = leggings, 38 = chestplate, 39 = helmet, 40 = offhand
     */
    private static final int OFFHAND_INV_SLOT  = 40;
    private static final int[] ARMOR_INV_SLOTS = {36, 37, 38, 39}; // boots to helmet

    /** Hotbar starts at screen-handler slot index 54 for a 3-row (27-slot) chest */
    private static final int HOTBAR_SCREEN_BASE = 54;

    public static KeyBinding stashAllKey;

    @Override
    public void onInitializeClient() {
        stashAllKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.enderchest_stash.stash_all",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                "category.enderchest_stash"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (stashAllKey.wasPressed()) {
                handleStashAll(client);
            }
        });
    }

    // Stash everything: hotbar slots 1,2,5,8,9 + offhand + all armor
    private void handleStashAll(MinecraftClient client) {
        GenericContainerScreenHandler handler = getEnderChestHandler(client);
        if (handler == null) return;

        // 1. Shift-click the desired hotbar screen slots directly
        for (int hi : HOTBAR_INDICES_TO_STASH) {
            int screenSlot = HOTBAR_SCREEN_BASE + hi;
            if (!handler.slots.get(screenSlot).getStack().isEmpty()) {
                quickMove(client, handler, screenSlot);
            }
        }

        // 2. Offhand
        if (!client.player.getOffHandStack().isEmpty()) {
            stashSpecialSlot(client, handler, OFFHAND_INV_SLOT);
        }

        // 3. Armor (boots, leggings, chestplate, helmet)
        for (int armorSlot : ARMOR_INV_SLOTS) {
            ItemStack armorStack = client.player.getInventory().getStack(armorSlot);
            if (!armorStack.isEmpty()) {
                stashSpecialSlot(client, handler, armorSlot);
            }
        }
    }

    /**
     * Stash a "special" inventory slot (armor or offhand) that is NOT exposed
     * in the ender chest screen handler.
     *
     * Steps:
     *   1. Find a free hotbar screen slot (staging).
     *   2. SWAP staging slot <-> special player-inventory slot.
     *   3. QUICK_MOVE staging slot into the ender chest.
     *   4. If the ender chest was full and item remains, swap it back.
     */
    private void stashSpecialSlot(MinecraftClient client,
                                   GenericContainerScreenHandler handler,
                                   int playerInvSlotIndex) {
        int stagingScreenSlot = findFreeHotbarScreenSlot(handler);
        if (stagingScreenSlot == -1) {
            client.player.sendMessage(
                    Text.literal("§eEnderChest Stash: No free hotbar slot for staging!"),
                    true
            );
            return;
        }

        // Swap: item from playerInvSlotIndex goes into stagingScreenSlot
        client.interactionManager.clickSlot(
                handler.syncId,
                stagingScreenSlot,
                playerInvSlotIndex,
                SlotActionType.SWAP,
                client.player
        );

        // Shift-click staging into the ender chest
        quickMove(client, handler, stagingScreenSlot);

        // If item didn't fit (chest full), restore it
        if (!handler.slots.get(stagingScreenSlot).getStack().isEmpty()) {
            client.interactionManager.clickSlot(
                    handler.syncId,
                    stagingScreenSlot,
                    playerInvSlotIndex,
                    SlotActionType.SWAP,
                    client.player
            );
            client.player.sendMessage(
                    Text.literal("§eEnderChest Stash: Ender chest is full!"),
                    true
            );
        }
    }

    /** Send a QUICK_MOVE (shift-click) packet for the given screen slot. */
    private void quickMove(MinecraftClient client,
                           GenericContainerScreenHandler handler,
                           int screenSlot) {
        client.interactionManager.clickSlot(
                handler.syncId,
                screenSlot,
                0,
                SlotActionType.QUICK_MOVE,
                client.player
        );
    }

    /**
     * Returns the first empty hotbar screen slot index (54-62), or -1 if all full.
     */
    private int findFreeHotbarScreenSlot(GenericContainerScreenHandler handler) {
        for (int i = 0; i < 9; i++) {
            Slot slot = handler.slots.get(HOTBAR_SCREEN_BASE + i);
            if (slot.getStack().isEmpty()) {
                return HOTBAR_SCREEN_BASE + i;
            }
        }
        return -1;
    }

    /**
     * Returns the GenericContainerScreenHandler if the player currently has a
     * 3-row (ender chest) container open, or null (with an error message).
     */
    private GenericContainerScreenHandler getEnderChestHandler(MinecraftClient client) {
        if (client.player == null) return null;
        if (!(client.player.currentScreenHandler instanceof GenericContainerScreenHandler handler)) {
            client.player.sendMessage(
                    Text.literal("§cEnderChest Stash: Open an ender chest first!"),
                    true
            );
            return null;
        }
        if (handler.getRows() != 3) {
            client.player.sendMessage(
                    Text.literal("§cEnderChest Stash: This doesn't look like an ender chest!"),
                    true
            );
            return null;
        }
        return handler;
    }
}
