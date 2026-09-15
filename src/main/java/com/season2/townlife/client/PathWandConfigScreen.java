package com.season2.townlife.client;

import com.season2.townlife.data.TownPathType;
import com.season2.townlife.item.PathEditMode;
import com.season2.townlife.network.TownPathNetwork;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;

/** Small operator UI for selecting how the Path Wand edits and prioritises roads. */
public final class PathWandConfigScreen extends Screen {
    private static final int PANEL_W = 350;
    private static final int PANEL_H = 240;

    private final InteractionHand hand;
    private final Map<PathEditMode, Button> modeButtons = new EnumMap<>(PathEditMode.class);
    private final Map<TownPathType, Button> typeButtons = new EnumMap<>(TownPathType.class);
    private PathEditMode mode;
    private TownPathType type;

    public PathWandConfigScreen(PathEditMode mode, TownPathType type, InteractionHand hand) {
        super(Component.literal("Path Wand Configuration"));
        this.mode = mode == null ? PathEditMode.ADD_CONNECTED : mode;
        this.type = type == null ? TownPathType.NORMAL : type;
        this.hand = hand == null ? InteractionHand.MAIN_HAND : hand;
    }

    @Override
    protected void init() {
        modeButtons.clear();
        typeButtons.clear();
        int left = (this.width - PANEL_W) / 2;
        int top = (this.height - PANEL_H) / 2;

        int y = top + 55;
        for (PathEditMode candidate : PathEditMode.values()) {
            PathEditMode captured = candidate;
            Button button = Button.builder(Component.literal(candidate.displayName()), ignored -> {
                        this.mode = captured;
                        refreshButtons();
                    })
                    .bounds(left + 18, y, 145, 20)
                    .build();
            modeButtons.put(candidate, this.addRenderableWidget(button));
            y += 24;
        }

        y = top + 55;
        for (TownPathType candidate : TownPathType.values()) {
            TownPathType captured = candidate;
            Button button = Button.builder(Component.literal(candidate.displayName()), ignored -> {
                        this.type = captured;
                        refreshButtons();
                    })
                    .bounds(left + 187, y, 145, 20)
                    .build();
            typeButtons.put(candidate, this.addRenderableWidget(button));
            y += 24;
        }

        this.addRenderableWidget(Button.builder(Component.literal("Save"), ignored -> saveAndClose())
                .bounds(left + 18, top + 204, 90, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("Highlight Nearby"), ignored -> highlightNearby())
                .bounds(left + 116, top + 204, 118, 20)
                .build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, ignored -> onClose())
                .bounds(left + 242, top + 204, 90, 20)
                .build());

        refreshButtons();
    }

    private void refreshButtons() {
        for (Map.Entry<PathEditMode, Button> entry : modeButtons.entrySet()) {
            boolean selected = entry.getKey() == mode;
            entry.getValue().setMessage(Component.literal((selected ? "> " : "") + entry.getKey().displayName()));
        }
        for (Map.Entry<TownPathType, Button> entry : typeButtons.entrySet()) {
            boolean selected = entry.getKey() == type;
            entry.getValue().setMessage(Component.literal((selected ? "> " : "") + entry.getKey().displayName()));
        }
    }

    private void saveAndClose() {
        TownPathNetwork.CHANNEL.sendToServer(new TownPathNetwork.UpdateSettingsPacket(mode, type, hand));
        if (this.minecraft != null) this.minecraft.setScreen(null);
    }

    private void highlightNearby() {
        TownPathNetwork.CHANNEL.sendToServer(new TownPathNetwork.InspectNearbyPacket(hand));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int left = (this.width - PANEL_W) / 2;
        int top = (this.height - PANEL_H) / 2;

        graphics.fill(0, 0, this.width, this.height, 0xAA101010);
        graphics.fill(left, top, left + PANEL_W, top + PANEL_H, 0xFF3F3428);
        graphics.fill(left + 3, top + 3, left + PANEL_W - 3, top + PANEL_H - 3, 0xFFD8C499);
        graphics.fill(left + 8, top + 8, left + PANEL_W - 8, top + PANEL_H - 8, 0xFFEADBB7);

        graphics.drawCenteredString(this.font, "Path Wand Configuration", left + PANEL_W / 2, top + 14, 0x3B2A19);
        graphics.drawString(this.font, "Edit Mode", left + 18, top + 39, 0x55351E, false);
        graphics.drawString(this.font, "Path Priority", left + 187, top + 39, 0x55351E, false);
        graphics.drawString(this.font,
                "Main roads are preferred; Avoid is used only when it is still the best practical route.",
                left + 18, top + 181, 0x6A5238, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
