package com.season2.dragoncurrency;

import net.minecraft.world.item.Item;

/**
 * Deprecated compatibility item retained so worlds containing the old pouch do not
 * get missing-item data. Banking replaced the pouch in v1.4.0.
 */
public final class CoinPouchItem extends Item {
    public CoinPouchItem(Properties properties) {
        super(properties);
    }
}
