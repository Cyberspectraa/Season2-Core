package com.season2.dragoncurrency;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** True one-slot Dragon Bank menu with the normal player inventory underneath. */
public final class BankMenu extends AbstractContainerMenu {
    private static final int BANK_SLOT = 0;
    private static final int PLAYER_SLOT_START = 1;
    private static final int PLAYER_SLOT_END = 37;

    private final BankContainer bank;
    private final ContainerData balanceData;

    /** Client constructor used by Forge's custom menu factory. */
    public BankMenu(int id, Inventory playerInventory, FriendlyByteBuf ignored) {
        this(id, playerInventory, new SimpleContainer(1), null, new SimpleContainerData(2));
    }

    /** Server constructor. */
    public BankMenu(int id, Inventory playerInventory, BankContainer bank) {
        this(id, playerInventory, bank, bank, createServerBalanceData(playerInventory));
    }

    private BankMenu(
            int id,
            Inventory playerInventory,
            Container depositContainer,
            BankContainer bank,
            ContainerData balanceData
    ) {
        super(DragonCurrency.BANK_MENU.get(), id);
        this.bank = bank;
        this.balanceData = balanceData;

        this.addSlot(new Slot(depositContainer, 0, 80, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return BankAccount.denominationIndex(stack) >= 0;
            }
        });

        // Main player inventory: slots 9-35.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(
                        playerInventory,
                        col + row * 9 + 9,
                        8 + col * 18,
                        84 + row * 18
                ));
            }
        }

        // Hotbar: slots 0-8.
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }

        this.addDataSlots(balanceData);
    }

    private static ContainerData createServerBalanceData(Inventory playerInventory) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                long balance = BankAccount.getBalance(playerInventory.player);
                return index == 0 ? (int) balance : (int) (balance >>> 32);
            }

            @Override
            public void set(int index, int value) {
                // Server balance is authoritative; client writes are ignored.
            }

            @Override
            public int getCount() {
                return 2;
            }
        };
    }

    public long getSyncedBalance() {
        long low = Integer.toUnsignedLong(balanceData.get(0));
        long high = Integer.toUnsignedLong(balanceData.get(1));
        return (high << 32) | low;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        if (slotIndex < PLAYER_SLOT_START || slotIndex >= PLAYER_SLOT_END || bank == null) {
            return ItemStack.EMPTY;
        }

        Slot slot = this.getSlot(slotIndex);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        if (BankAccount.denominationIndex(stack) < 0) return ItemStack.EMPTY;

        ItemStack original = stack.copy();
        int deposited = bank.depositFromPlayerStack(stack);
        if (deposited <= 0) return ItemStack.EMPTY;

        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }
}
