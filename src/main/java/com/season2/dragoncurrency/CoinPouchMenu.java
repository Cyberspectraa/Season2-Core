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
    public void m_150399_(int slotIndex, int button, ClickType clickType, Player player) {
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

        super.m_150399_(slotIndex, button, clickType, player);
    }

    @Override
    public ItemStack m_7648_(Player player, int slotIndex) {
        // The 3x3 pouch area contains controls/readouts plus one deposit slot.
        if (slotIndex >= 0 && slotIndex < CoinPouchContainer.SLOT_COUNT) {
            return ItemStack.f_41583_;
        }

        // Shift-clicking a coin deposits the entire stack directly into the
        // numerical wallet. Non-coins are intentionally ignored.
        Slot slot = this.m_38853_(slotIndex);
        if (slot == null || !slot.m_6657_()) {
            return ItemStack.f_41583_;
        }

        ItemStack stack = slot.m_7993_();
        if (!CoinPouchContainer.isCoin(stack)) {
            return ItemStack.f_41583_;
        }

        ItemStack original = stack.m_41777_();
        int deposited = pouch.depositFromPlayerStack(stack);
        if (deposited <= 0) {
            return ItemStack.f_41583_;
        }

        if (stack.m_41619_()) {
            slot.m_5852_(ItemStack.f_41583_);
        } else {
            slot.m_6654_();
        }

        return original;
    }
}
