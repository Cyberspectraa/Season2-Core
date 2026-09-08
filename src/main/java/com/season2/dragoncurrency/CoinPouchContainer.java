package com.season2.dragoncurrency;

import java.text.NumberFormat;
import java.util.Locale;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class CoinPouchContainer extends SimpleContainer {
    public static final int SLOT_COUNT = 9;
    private static final int LEGACY_SLOT_COUNT = 27;

    // Compact 3x3 layout:
    // [ previous ][ selected ][ next ]
    // [  empty   ][ deposit  ][ empty]
    // [ withdraw1][  empty   ][ withdraw64]
    public static final int PREVIOUS_SLOT = 0;
    public static final int BALANCE_DISPLAY_SLOT = 1;
    public static final int NEXT_SLOT = 2;
    public static final int DEPOSIT_SLOT = 4;
    public static final int WITHDRAW_ONE_SLOT = 6;
    public static final int WITHDRAW_STACK_SLOT = 8;

    private static final String WALLET_TAG = "DragonCurrencyWallet";
    private static final String LEGACY_INVENTORY_TAG = "DragonCurrencyPouch";
    private static final String SELECTED_KEY = "Selected";
    private static final String[] BALANCE_KEYS = {
            "Copper", "Silver", "Gold", "Platinum", "Dragon"
    };
    private static final String[] DISPLAY_NAMES = {
            "Copper", "Silver", "Gold", "Platinum", "Dragon"
    };
    private static final String[] DISPLAY_COLORS = {
            "gold", "gray", "yellow", "aqua", "light_purple"
    };

    private final ItemStack pouchStack;
    private final Player player;
    private boolean refreshing;
    private int selected;

    public CoinPouchContainer(ItemStack pouchStack, Player player) {
        super(SLOT_COUNT);
        this.pouchStack = pouchStack;
        this.player = player;

        migrateLegacyInventory();
        int savedSelected = wallet().getInt(SELECTED_KEY);
        this.selected = normalizeIndex(savedSelected);
        refreshVirtualSlots();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == DEPOSIT_SLOT && denominationIndex(stack) >= 0;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (refreshing) {
            super.setItem(slot, stack);
            return;
        }

        if (slot == DEPOSIT_SLOT) {
            deposit(stack);
            super.setItem(DEPOSIT_SLOT, ItemStack.EMPTY);
            refreshVirtualSlots();
            return;
        }

        // All other pouch-area slots are virtual controls/readouts.
        if (slot >= 0 && slot < SLOT_COUNT) {
            refreshVirtualSlots();
            return;
        }

        super.setItem(slot, stack);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot == PREVIOUS_SLOT) {
            cycle(-1);
            return ItemStack.EMPTY;
        }
        if (slot == NEXT_SLOT) {
            cycle(1);
            return ItemStack.EMPTY;
        }
        if (slot == WITHDRAW_ONE_SLOT) {
            withdraw(1);
            return ItemStack.EMPTY;
        }
        if (slot == WITHDRAW_STACK_SLOT) {
            withdraw(64);
            return ItemStack.EMPTY;
        }
        if (slot == BALANCE_DISPLAY_SLOT) {
            return ItemStack.EMPTY;
        }
        return super.removeItem(slot, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (isVirtualSlot(slot)) {
            return ItemStack.EMPTY;
        }
        return super.removeItemNoUpdate(slot);
    }

    public void cycle(int direction) {
        selected = normalizeIndex(selected + direction);
        wallet().putInt(SELECTED_KEY, selected);
        refreshVirtualSlots();
    }

    public void withdraw(int requested) {
        long balance = getBalance(selected);
        if (balance <= 0L) {
            refreshVirtualSlots();
            return;
        }

        int amount = (int) Math.min((long) requested, balance);
        ItemStack withdrawn = new ItemStack(realCoin(selected), amount);
        int before = withdrawn.getCount();

        player.getInventory().add(withdrawn);

        int inserted = before - withdrawn.getCount();
        if (inserted > 0) {
            setBalance(selected, balance - inserted);
        }
        refreshVirtualSlots();
    }

    public int selectedIndex() {
        return selected;
    }

    public long selectedBalance() {
        return getBalance(selected);
    }

    /**
     * Deposits coins directly from a player inventory stack (used by shift-click).
     * The accepted coins are removed from the supplied stack and added to this pouch.
     *
     * @return number of coins accepted
     */
    public int depositFromPlayerStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }

        int denomination = denominationIndex(stack);
        if (denomination < 0) {
            return 0;
        }

        long current = getBalance(denomination);
        long remainingCapacity = Long.MAX_VALUE - current;
        if (remainingCapacity <= 0L) {
            return 0;
        }

        int accepted = (int) Math.min((long) stack.getCount(), remainingCapacity);
        if (accepted <= 0) {
            return 0;
        }

        setBalance(denomination, current + accepted);
        stack.shrink(accepted);
        refreshVirtualSlots();
        return accepted;
    }

    private void deposit(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        int denomination = denominationIndex(stack);
        if (denomination < 0) {
            return;
        }

        long current = getBalance(denomination);
        int count = stack.getCount();
        if (count <= 0) {
            return;
        }

        long updated = current > Long.MAX_VALUE - count ? Long.MAX_VALUE : current + count;
        setBalance(denomination, updated);
    }

    private void migrateLegacyInventory() {
        CompoundTag legacy = pouchStack.getTagElement(LEGACY_INVENTORY_TAG);
        if (legacy == null) {
            return;
        }

        for (int slot = 0; slot < LEGACY_SLOT_COUNT; slot++) {
            CompoundTag slotTag = legacy.getCompound("Slot" + slot);
            ItemStack legacyStack = ItemStack.of(slotTag);
            int denomination = denominationIndex(legacyStack);
            if (denomination >= 0 && !legacyStack.isEmpty()) {
                long current = getBalance(denomination);
                int count = legacyStack.getCount();
                long updated = current > Long.MAX_VALUE - count ? Long.MAX_VALUE : current + count;
                setBalance(denomination, updated);
            }
        }

        pouchStack.removeTagKey(LEGACY_INVENTORY_TAG);
    }

    private long getBalance(int denomination) {
        long value = wallet().getLong(BALANCE_KEYS[denomination]);
        return Math.max(0L, value);
    }

    private void setBalance(int denomination, long value) {
        wallet().putLong(BALANCE_KEYS[denomination], Math.max(0L, value));
    }

    private CompoundTag wallet() {
        return pouchStack.getOrCreateTagElement(WALLET_TAG);
    }

    private void refreshVirtualSlots() {
        refreshing = true;
        try {
            super.setItem(PREVIOUS_SLOT, new ItemStack(DragonCurrency.PREVIOUS_BUTTON.get()));
            super.setItem(NEXT_SLOT, new ItemStack(DragonCurrency.NEXT_BUTTON.get()));
            super.setItem(WITHDRAW_ONE_SLOT, new ItemStack(DragonCurrency.WITHDRAW_ONE_BUTTON.get()));
            super.setItem(WITHDRAW_STACK_SLOT, new ItemStack(DragonCurrency.WITHDRAW_STACK_BUTTON.get()));
            super.setItem(BALANCE_DISPLAY_SLOT, makeBalanceDisplay());
            super.setItem(DEPOSIT_SLOT, ItemStack.EMPTY);
        } finally {
            refreshing = false;
        }
        super.setChanged();
    }

    private ItemStack makeBalanceDisplay() {
        ItemStack display = new ItemStack(displayItem(selected));
        long balance = getBalance(selected);
        String formatted = NumberFormat.getIntegerInstance(Locale.US).format(balance);
        String name = DISPLAY_NAMES[selected] + " Balance: " + formatted;
        String json = "{\"text\":\"" + name + "\",\"italic\":false,\"color\":\"" + DISPLAY_COLORS[selected] + "\"}";
        display.getOrCreateTagElement("display").putString("Name", json);
        return display;
    }

    private static boolean isVirtualSlot(int slot) {
        return slot == PREVIOUS_SLOT
                || slot == BALANCE_DISPLAY_SLOT
                || slot == NEXT_SLOT
                || slot == WITHDRAW_ONE_SLOT
                || slot == WITHDRAW_STACK_SLOT;
    }

    public static boolean isCoin(ItemStack stack) {
        return denominationIndex(stack) >= 0;
    }

    public static int denominationIndex(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return -1;
        }
        Item item = stack.getItem();
        if (item == DragonCurrency.COPPER_COIN.get()) return 0;
        if (item == DragonCurrency.SILVER_COIN.get()) return 1;
        if (item == DragonCurrency.GOLD_COIN.get()) return 2;
        if (item == DragonCurrency.PLATINUM_COIN.get()) return 3;
        if (item == DragonCurrency.DRAGON_COIN.get()) return 4;
        return -1;
    }

    private static Item realCoin(int index) {
        return switch (normalizeIndex(index)) {
            case 0 -> DragonCurrency.COPPER_COIN.get();
            case 1 -> DragonCurrency.SILVER_COIN.get();
            case 2 -> DragonCurrency.GOLD_COIN.get();
            case 3 -> DragonCurrency.PLATINUM_COIN.get();
            default -> DragonCurrency.DRAGON_COIN.get();
        };
    }

    private static Item displayItem(int index) {
        return switch (normalizeIndex(index)) {
            case 0 -> DragonCurrency.COPPER_DISPLAY.get();
            case 1 -> DragonCurrency.SILVER_DISPLAY.get();
            case 2 -> DragonCurrency.GOLD_DISPLAY.get();
            case 3 -> DragonCurrency.PLATINUM_DISPLAY.get();
            default -> DragonCurrency.DRAGON_DISPLAY.get();
        };
    }

    private static int normalizeIndex(int value) {
        int result = value % 5;
        return result < 0 ? result + 5 : result;
    }
}
