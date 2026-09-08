package uk.co.cyberspectra.spectralmail;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;

/** Parchment-style physical letter composer with a server-supplied recipient picker. */
public final class LetterComposeScreen extends Screen {
    private static final int PANEL_W = 386;
    private static final int PANEL_H = 244;
    private static final int RECIPIENT_ROWS = 8;

    private final List<ComposeRecipient> recipients;
    private final int maxLength;
    private final InteractionHand hand;

    private List<ComposeRecipient> filteredRecipients = List.of();
    private ComposeRecipient selected;
    private int recipientPage;
    private EditBox searchBox;
    private MultiLineEditBox messageBox;
    private Button addressButton;

    public LetterComposeScreen(List<ComposeRecipient> recipients, int maxLength, InteractionHand hand) {
        super(Component.literal("Write Letter"));
        this.recipients = recipients == null ? List.of() : List.copyOf(recipients);
        this.maxLength = Math.max(32, maxLength);
        this.hand = hand == null ? InteractionHand.MAIN_HAND : hand;
    }

    @Override
    protected void init() {
        int left = (this.width - PANEL_W) / 2;
        int top = (this.height - PANEL_H) / 2;
        int rightX = left + 154;

        this.searchBox = new EditBox(this.font, left + 18, top + 35, 118, 18, Component.literal("Search players"));
        this.searchBox.setMaxLength(64);
        this.searchBox.setResponder(this::applyFilter);
        this.addRenderableWidget(this.searchBox);

        this.messageBox = new MultiLineEditBox(
                this.font,
                rightX,
                top + 52,
                214,
                124,
                Component.literal("Write your message..."),
                Component.literal("Letter message"));
        this.messageBox.setCharacterLimit(this.maxLength);
        this.addRenderableWidget(this.messageBox);

        this.addressButton = this.addRenderableWidget(Button.builder(
                        Component.literal("Address Letter"), button -> submit())
                .bounds(rightX, top + 197, 132, 20)
                .build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(rightX + 138, top + 197, 76, 20)
                .build());

        applyFilter("");
        this.searchBox.setFocused(true);
        this.setInitialFocus(this.searchBox);
    }

    private void applyFilter(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<ComposeRecipient> filtered = new ArrayList<>();
        for (ComposeRecipient recipient : this.recipients) {
            if (needle.isEmpty() || recipient.name().toLowerCase(Locale.ROOT).contains(needle)) {
                filtered.add(recipient);
            }
        }
        this.filteredRecipients = List.copyOf(filtered);
        int pageCount = pageCount();
        if (recipientPage >= pageCount) recipientPage = Math.max(0, pageCount - 1);
    }

    private int pageCount() {
        return Math.max(1, (this.filteredRecipients.size() + RECIPIENT_ROWS - 1) / RECIPIENT_ROWS);
    }

    private void submit() {
        if (this.selected == null || this.messageBox == null) return;
        String message = this.messageBox.getValue().trim();
        if (message.isEmpty()) return;
        MailNetwork.CHANNEL.sendToServer(new MailNetwork.SubmitDraftPacket(
                this.selected.uuid(), message, this.hand));
        if (this.minecraft != null) this.minecraft.setScreen(null);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int left = (this.width - PANEL_W) / 2;
        int top = (this.height - PANEL_H) / 2;
        int rightX = left + 154;

        graphics.fill(0, 0, this.width, this.height, 0xAA101010);
        graphics.fill(left, top, left + PANEL_W, top + PANEL_H, 0xFF4B3724);
        graphics.fill(left + 3, top + 3, left + PANEL_W - 3, top + PANEL_H - 3, 0xFFE6D2A2);
        graphics.fill(left + 8, top + 8, left + PANEL_W - 8, top + PANEL_H - 8, 0xFFF2E2B8);
        graphics.fill(left + 145, top + 26, left + 146, top + PANEL_H - 17, 0xFFB89A67);

        graphics.drawCenteredString(this.font, "Write Correspondence", left + PANEL_W / 2, top + 13, 0x4A2C17);
        graphics.drawString(this.font, "Recipient", left + 18, top + 24, 0x55351E, false);
        graphics.drawString(this.font, "Message", rightX, top + 37, 0x55351E, false);

        if (this.filteredRecipients.isEmpty()) {
            graphics.drawString(this.font, "No known players", left + 23, top + 68, 0x7A5B3B, false);
        } else {
            int start = this.recipientPage * RECIPIENT_ROWS;
            int end = Math.min(this.filteredRecipients.size(), start + RECIPIENT_ROWS);
            for (int index = start; index < end; index++) {
                ComposeRecipient recipient = this.filteredRecipients.get(index);
                int row = index - start;
                int rowY = top + 61 + row * 17;
                boolean isSelected = this.selected != null && this.selected.uuid().equals(recipient.uuid());
                boolean hovered = mouseX >= left + 18 && mouseX <= left + 136
                        && mouseY >= rowY && mouseY < rowY + 15;
                if (isSelected) graphics.fill(left + 18, rowY, left + 136, rowY + 15, 0xFFD0B47B);
                else if (hovered) graphics.fill(left + 18, rowY, left + 136, rowY + 15, 0x55B89A67);
                String name = this.font.plainSubstrByWidth(recipient.name(), 112);
                graphics.drawString(this.font, name, left + 22, rowY + 3, 0x3B2A19, false);
            }
        }

        int pages = pageCount();
        String pageText = (this.recipientPage + 1) + " / " + pages;
        graphics.drawCenteredString(this.font, pageText, left + 77, top + 207, 0x6A5238);
        if (this.recipientPage > 0) {
            graphics.fill(left + 18, top + 201, left + 43, top + 218, 0xFFD0B47B);
            graphics.drawCenteredString(this.font, "<", left + 30, top + 205, 0x3B2A19);
        }
        if (this.recipientPage + 1 < pages) {
            graphics.fill(left + 111, top + 201, left + 136, top + 218, 0xFFD0B47B);
            graphics.drawCenteredString(this.font, ">", left + 123, top + 205, 0x3B2A19);
        }

        String selectedName = this.selected == null ? "None" : this.selected.name();
        graphics.drawString(this.font, "To: " + this.font.plainSubstrByWidth(selectedName, 168),
                rightX, top + 181, 0x55351E, false);
        int chars = this.messageBox == null ? 0 : this.messageBox.getValue().length();
        graphics.drawString(this.font, chars + " / " + this.maxLength,
                rightX + 164, top + 181, chars > this.maxLength ? 0xAA2222 : 0x6A5238, false);

        if (this.addressButton != null) {
            this.addressButton.active = this.selected != null && this.messageBox != null
                    && !this.messageBox.getValue().trim().isEmpty()
                    && this.messageBox.getValue().length() <= this.maxLength;
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int left = (this.width - PANEL_W) / 2;
            int top = (this.height - PANEL_H) / 2;

            int start = this.recipientPage * RECIPIENT_ROWS;
            int end = Math.min(this.filteredRecipients.size(), start + RECIPIENT_ROWS);
            for (int index = start; index < end; index++) {
                int row = index - start;
                int rowY = top + 61 + row * 17;
                if (mouseX >= left + 18 && mouseX <= left + 136 && mouseY >= rowY && mouseY < rowY + 15) {
                    this.selected = this.filteredRecipients.get(index);
                    if (this.messageBox != null) this.setFocused(this.messageBox);
                    return true;
                }
            }

            if (this.recipientPage > 0
                    && mouseX >= left + 18 && mouseX <= left + 43
                    && mouseY >= top + 201 && mouseY <= top + 218) {
                this.recipientPage--;
                return true;
            }
            if (this.recipientPage + 1 < pageCount()
                    && mouseX >= left + 111 && mouseX <= left + 136
                    && mouseY >= top + 201 && mouseY <= top + 218) {
                this.recipientPage++;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int left = (this.width - PANEL_W) / 2;
        int top = (this.height - PANEL_H) / 2;
        if (mouseX >= left + 15 && mouseX <= left + 139 && mouseY >= top + 58 && mouseY <= top + 220) {
            if (delta > 0 && this.recipientPage > 0) this.recipientPage--;
            else if (delta < 0 && this.recipientPage + 1 < pageCount()) this.recipientPage++;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
