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
    public boolean m_7013_(int slot, ItemStack stack) {
        return slot == DEPOSIT_SLOT && BankAccount.denominationIndex(stack) >= 0;
    }

    @Override
    public void m_6836_(int slot, ItemStack stack) {
        if (depositing) {
            super.m_6836_(slot, stack);
            return;
        }

        if (slot == DEPOSIT_SLOT) {
            if (stack != null && !stack.m_41619_()) {
                int denomination = BankAccount.denominationIndex(stack);
                if (denomination >= 0) {
                    BankAccount.deposit(player, denomination, stack.m_41613_());
                }
            }

            depositing = true;
            try {
                super.m_6836_(DEPOSIT_SLOT, ItemStack.f_41583_);
            } finally {
                depositing = false;
            }
            super.m_6596_();
            return;
        }

        super.m_6836_(slot, stack);
    }

    public int depositFromPlayerStack(ItemStack stack) {
        int denomination = BankAccount.denominationIndex(stack);
        if (denomination < 0) return 0;

        int requested = stack.m_41613_();
        long accepted = BankAccount.deposit(player, denomination, requested);
        if (accepted <= 0L) return 0;

        int acceptedInt = (int) accepted;
        stack.m_41774_(acceptedInt);
        super.m_6596_();
        return acceptedInt;
    }
}
