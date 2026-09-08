package com.season2.dragoncurrency;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * One-slot banker deposit container. Coins are immediately converted into the
 * player's persistent bank balance, so no physical stack remains in the slot.
 */
public final class BankContainer extends SimpleContainer {
    public static final int SLOT_COUNT = 1;
    public static final int DEPOSIT_SLOT = 0;

    private final Player player;
    private boolean depositing;

    public BankContainer(Player player) {
        super(SLOT_COUNT);
        this.player = player;
        BankAccount.migrateLegacyPouches(player);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == DEPOSIT_SLOT && BankAccount.denominationIndex(stack) >= 0;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (depositing) {
            super.setItem(slot, stack);
            return;
        }

        if (slot == DEPOSIT_SLOT) {
            if (stack != null && !stack.isEmpty()) {
                int denomination = BankAccount.denominationIndex(stack);
                if (denomination >= 0) {
                    BankAccount.deposit(player, denomination, stack.getCount());
                }
            }

            depositing = true;
            try {
                super.setItem(DEPOSIT_SLOT, ItemStack.EMPTY);
            } finally {
                depositing = false;
            }
            super.setChanged();
            return;
        }

        super.setItem(slot, stack);
    }

    public int depositFromPlayerStack(ItemStack stack) {
        int denomination = BankAccount.denominationIndex(stack);
        if (denomination < 0) return 0;

        int requested = stack.getCount();
        long accepted = BankAccount.deposit(player, denomination, requested);
        if (accepted <= 0L) return 0;

        int acceptedInt = (int) accepted;
        stack.shrink(acceptedInt);
        super.setChanged();
        return acceptedInt;
    }
}
