package com.season2.townlife.client;

import com.season2.townlife.item.TownWandAction;
import com.season2.townlife.network.TownWandNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Deliberate one-action-at-a-time GUI. Sneak-clicks never deselect residents. */
public final class TownWandConfigScreen extends Screen {
    private static final int W = 366;
    private static final int H = 244;
    private final TownWandNetwork.OpenPacket details;

    public TownWandConfigScreen(TownWandNetwork.OpenPacket details) {
        super(Component.literal("Town Wand Configuration"));
        this.details = details;
    }

    @Override
    protected void init() {
        int x = (width - W) / 2;
        int y = (height - H) / 2;
        boolean selected = details.selectedLoaded();
        addAction(x + 12, y + 102, 164, "Assign Home", TownWandAction.ASSIGN_HOME, selected);
        addAction(x + 190, y + 102, 164, "Assign Workplace", TownWandAction.ASSIGN_WORKPLACE, selected);
        addAction(x + 12, y + 128, 164, "Assign Work Position", TownWandAction.ASSIGN_POSITION, selected);
        addAction(x + 190, y + 128, 164, "Cancel Pending Action", TownWandAction.NONE, true);
        addAction(x + 12, y + 170, 164, "Clear Work Position", TownWandAction.CLEAR_POSITION,
                selected && !details.position().equals("Not assigned"));
        addAction(x + 190, y + 170, 164, "Clear Selection", TownWandAction.CLEAR_SELECTION,
                !details.name().equals("None"));
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(x + 133, y + 209, 100, 20).build());
    }

    private void addAction(int x, int y, int width, String name, TownWandAction action, boolean active) {
        Button button = Button.builder(Component.literal(name), ignored -> {
            TownWandNetwork.CHANNEL.sendToServer(new TownWandNetwork.ActionPacket(details.hand(), action));
            onClose();
        }).bounds(x, y, width, 20).build();
        button.active = active;
        addRenderableWidget(button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = (width - W) / 2;
        int y = (height - H) / 2;
        graphics.fill(0, 0, width, height, 0xB0101010);
        graphics.fill(x, y, x + W, y + H, 0xFF3E3527);
        graphics.fill(x + 3, y + 3, x + W - 3, y + H - 3, 0xFFE6D6B2);
        graphics.drawCenteredString(font, "Town Wand Configuration", x + W / 2, y + 10, 0x362917);
        graphics.drawString(font, "Resident: " + details.name() + "    Job: " + details.job(), x + 12, y + 28, 0x362917, false);
        graphics.drawString(font, "Home: " + details.home() + "    Workplace: " + details.workplace(), x + 12, y + 41, 0x362917, false);
        graphics.drawString(font, "Work position: " + details.position(), x + 12, y + 54, 0x362917, false);
        graphics.drawString(font, "Active action: " + details.action().label(), x + 12, y + 67, 0x362917, false);
        int lineY = y + 80;
        for (FormattedCharSequence line : font.split(Component.literal(details.schedule()), W - 24)) {
            graphics.drawString(font, line, x + 12, lineY, 0x362917, false);
            lineY += 9;
        }
        graphics.drawString(font, "Choose a task; right-click its target block. Clear actions work immediately.",
                x + 12, y + 155, 0x634A25, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
