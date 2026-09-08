package com.season2.dragoncurrency;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Compact 3x3 pouch menu. The vanilla dispenser screen is reused because it
 * already provides a clean 3x3 panel above the player's inventory.
 */
public final class CoinPouchMenu extends DispenserMenu {
    private final CoinPouchContainer pouch;

    public CoinPouchMenu(int id, Inventory playerInventory, CoinPouchContainer pouch) {
        super(id, playerInventory, pouch);
        this.pouch = pouch;
    }

    @Override
    public void clicked(int slotIndex, int button, ClickType clickType, Player player) {
        if (slotIndex >= 0 && slotIndex < CoinPouchContainer.SLOT_COUNT) {
            if (slotIndex == CoinPouchContainer.PREVIOUS_SLOT) {
                pouch.cycle(-1);
                return;
            }
            if (slotIndex == CoinPouchContainer.NEXT_SLOT) {
                pouch.cycle(1);
                return;
            }
            if (slotIndex == CoinPouchContainer.WITHDRAW_ONE_SLOT) {
                pouch.withdraw(1);
                return;
            }
            if (slotIndex == CoinPouchContainer.WITHDRAW_STACK_SLOT) {
                pouch.withdraw(64);
                return;
            }
            if (slotIndex != CoinPouchContainer.DEPOSIT_SLOT) {
                return;
            }
        }

        super.clicked(slotIndex, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        // The 3x3 pouch area contains controls/readouts plus one deposit slot.
        if (slotIndex >= 0 && slotIndex < CoinPouchContainer.SLOT_COUNT) {
            return ItemStack.EMPTY;
        }

        // Shift-clicking a coin deposits the entire stack directly into the
        // numerical wallet. Non-coins are intentionally ignored.
        Slot slot = this.getSlot(slotIndex);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        if (!CoinPouchContainer.isCoin(stack)) {
            return ItemStack.EMPTY;
        }

        ItemStack original = stack.copy();
        int deposited = pouch.depositFromPlayerStack(stack);
        if (deposited <= 0) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        return original;
    }
}
