package com.season2.dragoncurrency;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Server-side persistent Dragon Bank balance stored in Forge's player-persisted NBT. */
public final class BankAccount {
    public static final long[] COIN_VALUES = {1L, 10L, 100L, 1_000L, 10_000L};

    private static final String PERSISTED_TAG = "PlayerPersisted";
    private static final String MOD_TAG = "dragoncurrency";
    private static final String BALANCE_KEY = "BankBalance";
    private static final String SELECTED_KEY = "BankSelectedCoin";
    private static final String OLD_WALLET_TAG = "DragonCurrencyWallet";
    private static final String MIGRATED_KEY = "PouchMigrationChecked";
    private static final String[] OLD_BALANCE_KEYS = {"Copper", "Silver", "Gold", "Platinum", "Dragon"};

    private BankAccount() {}

    public static long getBalance(Player player) {
        return Math.max(0L, data(player).m_128454_(BALANCE_KEY));
    }

    public static void setBalance(Player player, long balance) {
        data(player).m_128356_(BALANCE_KEY, Math.max(0L, balance));
    }

    public static int getSelected(Player player) {
        return normalize(data(player).m_128451_(SELECTED_KEY));
    }

    public static void setSelected(Player player, int selected) {
        data(player).m_128405_(SELECTED_KEY, normalize(selected));
    }

    public static long deposit(Player player, int denomination, int requestedCoins) {
        if (denomination < 0 || denomination >= COIN_VALUES.length || requestedCoins <= 0) {
            return 0L;
        }
        long unit = COIN_VALUES[denomination];
        long current = getBalance(player);
        long capacity = Long.MAX_VALUE - current;
        long acceptedCoins = Math.min((long) requestedCoins, capacity / unit);
        if (acceptedCoins <= 0L) return 0L;
        setBalance(player, current + acceptedCoins * unit);
        return acceptedCoins;
    }

    public static long withdrawableCoins(Player player, int denomination, int requestedCoins) {
        if (denomination < 0 || denomination >= COIN_VALUES.length || requestedCoins <= 0) return 0L;
        return Math.min((long) requestedCoins, getBalance(player) / COIN_VALUES[denomination]);
    }

    /**
     * Removes up to the requested number of coins without allowing multiplication
     * overflow or a negative balance. The bank balance remains server-authoritative.
     */
    public static void debit(Player player, int denomination, long coins) {
        if (denomination < 0 || denomination >= COIN_VALUES.length || coins <= 0L) return;
        long unit = COIN_VALUES[denomination];
        long current = getBalance(player);
        long affordableCoins = current / unit;
        long debitedCoins = Math.min(coins, affordableCoins);
        if (debitedCoins <= 0L) return;
        setBalance(player, current - debitedCoins * unit);
    }

    /**
     * One-time migration for v1.3.x pouch balances. Any wallet data found in old
     * pouches in the player's inventory is converted to base currency value and removed.
     */
    public static void migrateLegacyPouches(Player player) {
        CompoundTag account = data(player);
        if (account.m_128451_(MIGRATED_KEY) == 1) return;

        Inventory inventory = player.m_150109_();
        long migratedValue = 0L;
        for (int slot = 0; slot < inventory.m_6643_(); slot++) {
            ItemStack stack = inventory.m_8020_(slot);
            if (stack == null || stack.m_41619_() || stack.m_41720_() != DragonCurrency.COIN_POUCH.get()) continue;

            CompoundTag wallet = stack.m_41737_(OLD_WALLET_TAG);
            if (wallet == null) continue;

            for (int i = 0; i < OLD_BALANCE_KEYS.length; i++) {
                long coinCount = Math.max(0L, wallet.m_128454_(OLD_BALANCE_KEYS[i]));
                if (coinCount <= 0L) continue;
                long unit = COIN_VALUES[i];
                long add = coinCount > Long.MAX_VALUE / unit ? Long.MAX_VALUE : coinCount * unit;
                migratedValue = migratedValue > Long.MAX_VALUE - add ? Long.MAX_VALUE : migratedValue + add;
            }
            stack.m_41749_(OLD_WALLET_TAG);
        }

        if (migratedValue > 0L) {
            long current = getBalance(player);
            setBalance(player, current > Long.MAX_VALUE - migratedValue ? Long.MAX_VALUE : current + migratedValue);
        }
        account.m_128405_(MIGRATED_KEY, 1);
    }

    public static int denominationIndex(ItemStack stack) {
        if (stack == null || stack.m_41619_()) return -1;
        Item item = stack.m_41720_();
        if (item == DragonCurrency.COPPER_COIN.get()) return 0;
        if (item == DragonCurrency.SILVER_COIN.get()) return 1;
        if (item == DragonCurrency.GOLD_COIN.get()) return 2;
        if (item == DragonCurrency.PLATINUM_COIN.get()) return 3;
        if (item == DragonCurrency.DRAGON_COIN.get()) return 4;
        return -1;
    }

    public static Item coinItem(int index) {
        return switch (normalize(index)) {
            case 0 -> DragonCurrency.COPPER_COIN.get();
            case 1 -> DragonCurrency.SILVER_COIN.get();
            case 2 -> DragonCurrency.GOLD_COIN.get();
            case 3 -> DragonCurrency.PLATINUM_COIN.get();
            default -> DragonCurrency.DRAGON_COIN.get();
        };
    }

    public static Item displayItem(int index) {
        return switch (normalize(index)) {
            case 0 -> DragonCurrency.COPPER_DISPLAY.get();
            case 1 -> DragonCurrency.SILVER_DISPLAY.get();
            case 2 -> DragonCurrency.GOLD_DISPLAY.get();
            case 3 -> DragonCurrency.PLATINUM_DISPLAY.get();
            default -> DragonCurrency.DRAGON_DISPLAY.get();
        };
    }

    private static CompoundTag data(Player player) {
        CompoundTag root = player.getPersistentData();
        CompoundTag persisted = root.m_128469_(PERSISTED_TAG);
        CompoundTag mod = persisted.m_128469_(MOD_TAG);
        persisted.m_128365_(MOD_TAG, mod);
        root.m_128365_(PERSISTED_TAG, persisted);
        return mod;
    }

    private static int normalize(int value) {
        int result = value % 5;
        return result < 0 ? result + 5 : result;
    }
}
