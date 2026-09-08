package uk.co.cyberspectra.spectralmail;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Lightweight parchment reader. All content comes from a server-created physical letter item. */
public final class LetterScreen extends Screen {
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm").withZone(ZoneId.systemDefault());

    private final String sender;
    private final String sentAt;
    private final String message;
    private List<List<String>> pages = List.of(List.of(""));
    private int page;

    public LetterScreen(ItemStack stack) {
        super(Component.literal("Letter"));
        String rawSender = MailItemData.sender(stack);
        this.sender = rawSender == null || rawSender.isBlank() ? "Unknown" : rawSender;
        long time = MailItemData.sentAt(stack);
        this.sentAt = time > 0L ? DATE.format(Instant.ofEpochMilli(time)) : "Unknown date";
        String rawMessage = MailItemData.message(stack);
        this.message = rawMessage == null ? "" : rawMessage;
    }

    @Override
    protected void init() {
        this.pages = paginate(message, 190, 12);
        if (this.page >= this.pages.size()) this.page = Math.max(0, this.pages.size() - 1);
    }

    private List<List<String>> paginate(String text, int width, int linesPerPage) {
        List<String> lines = new ArrayList<>();
        String normalized = text.replace("\r", "");
        String[] paragraphs = normalized.split("\n", -1);
        for (String paragraph : paragraphs) {
            if (paragraph.isEmpty()) {
                lines.add("");
                continue;
            }
            StringBuilder current = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                String candidate = current.length() == 0 ? word : current + " " + word;
                if (this.font.width(candidate) <= width || current.length() == 0) {
                    current.setLength(0);
                    current.append(candidate);
                } else {
                    lines.add(current.toString());
                    current.setLength(0);
                    current.append(word);
                }
            }
            if (current.length() > 0) lines.add(current.toString());
        }
        if (lines.isEmpty()) lines.add("");

        List<List<String>> result = new ArrayList<>();
        for (int i = 0; i < lines.size(); i += linesPerPage) {
            result.add(new ArrayList<>(lines.subList(i, Math.min(lines.size(), i + linesPerPage))));
        }
        return result.isEmpty() ? List.of(List.of("")) : result;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int panelW = 246;
        int panelH = 196;
        int left = (this.width - panelW) / 2;
        int top = (this.height - panelH) / 2;

        graphics.fill(0, 0, this.width, this.height, 0xAA101010);
        graphics.fill(left, top, left + panelW, top + panelH, 0xFF4B3724);
        graphics.fill(left + 3, top + 3, left + panelW - 3, top + panelH - 3, 0xFFE6D2A2);
        graphics.fill(left + 8, top + 8, left + panelW - 8, top + panelH - 8, 0xFFF2E2B8);

        graphics.drawCenteredString(this.font, "Correspondence", left + panelW / 2, top + 15, 0x4A2C17);
        graphics.drawString(this.font, "From: " + sender, left + 18, top + 32, 0x55351E, false);
        graphics.drawString(this.font, sentAt, left + 18, top + 43, 0x6A5238, false);
        graphics.fill(left + 16, top + 57, left + panelW - 16, top + 58, 0xFFB89A67);

        List<String> pageLines = pages.get(Math.max(0, Math.min(page, pages.size() - 1)));
        int y = top + 68;
        for (String line : pageLines) {
            graphics.drawString(this.font, line, left + 28, y, 0x3B2A19, false);
            y += 10;
        }

        String pageText = "Page " + (page + 1) + " / " + pages.size();
        graphics.drawCenteredString(this.font, pageText, left + panelW / 2, top + 172, 0x5B432B);

        if (page > 0) {
            graphics.fill(left + 18, top + 166, left + 66, top + 185, 0xFFD0B47B);
            graphics.drawCenteredString(this.font, "< Prev", left + 42, top + 172, 0x3B2A19);
        }
        if (page + 1 < pages.size()) {
            graphics.fill(left + panelW - 66, top + 166, left + panelW - 18, top + 185, 0xFFD0B47B);
            graphics.drawCenteredString(this.font, "Next >", left + panelW - 42, top + 172, 0x3B2A19);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int panelW = 246;
        int panelH = 196;
        int left = (this.width - panelW) / 2;
        int top = (this.height - panelH) / 2;
        if (page > 0 && mouseX >= left + 18 && mouseX <= left + 66 && mouseY >= top + 166 && mouseY <= top + 185) {
            page--;
            return true;
        }
        if (page + 1 < pages.size() && mouseX >= left + panelW - 66 && mouseX <= left + panelW - 18 && mouseY >= top + 166 && mouseY <= top + 185) {
            page++;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 263 && page > 0) {
            page--;
            return true;
        }
        if (keyCode == 262 && page + 1 < pages.size()) {
            page++;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
