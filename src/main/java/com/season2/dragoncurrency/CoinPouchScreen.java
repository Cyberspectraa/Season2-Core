package com.season2.dragoncurrency;

import java.text.NumberFormat;
import java.util.Locale;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** Minimal Dragon Bank screen: one deposit slot and one balance readout. */
public final class CoinPouchScreen extends AbstractContainerScreen<BankMenu> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(DragonCurrency.MODID, "textures/gui/bank.png");

    public CoinPouchScreen(BankMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.f_97726_ = 176;
        this.f_97727_ = 166;
        this.f_97728_ = 8;
        this.f_97729_ = 6;
        this.f_97730_ = 8;
        this.f_97731_ = 73;
    }

    @Override
    public void m_88315_(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.m_280273_(graphics);
        super.m_88315_(graphics, mouseX, mouseY, partialTick);
        this.m_280072_(graphics, mouseX, mouseY);
    }

    @Override
    protected void m_7286_(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.m_280218_(
                TEXTURE,
                this.f_97735_,
                this.f_97736_,
                0,
                0,
                this.f_97726_,
                this.f_97727_
        );
    }

    @Override
    protected void m_280003_(GuiGraphics graphics, int mouseX, int mouseY) {
        String formatted = NumberFormat.getIntegerInstance(Locale.US)
                .format(this.f_97732_.getSyncedBalance());

        graphics.m_280056_(this.f_96547_, "Dragon Bank", 8, 6, 0x404040, false);
        graphics.m_280056_(this.f_96547_, "Balance: " + formatted, 14, 21, 0x404040, false);
        graphics.m_280056_(this.f_96547_, "Deposit", 68, 56, 0x404040, false);
        graphics.m_280056_(this.f_96547_, "Inventory", 8, 73, 0x404040, false);
    }
}
