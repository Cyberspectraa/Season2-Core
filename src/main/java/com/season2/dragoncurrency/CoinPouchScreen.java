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
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 73;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(
                TEXTURE,
                this.leftPos,
                this.topPos,
                0,
                0,
                this.imageWidth,
                this.imageHeight
        );
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        String formatted = NumberFormat.getIntegerInstance(Locale.US)
                .format(this.menu.getSyncedBalance());

        graphics.drawString(this.font, "Dragon Bank", 8, 6, 0x404040, false);
        graphics.drawString(this.font, "Balance: " + formatted, 14, 21, 0x404040, false);
        graphics.drawString(this.font, "Deposit", 68, 56, 0x404040, false);
        graphics.drawString(this.font, "Inventory", 8, 73, 0x404040, false);
    }
}
