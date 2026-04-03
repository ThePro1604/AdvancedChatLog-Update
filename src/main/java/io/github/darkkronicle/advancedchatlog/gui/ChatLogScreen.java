/*
 * Copyright (C) 2021-2026 DarkKronicle
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.github.darkkronicle.advancedchatlog.gui;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.malilib.util.StringUtils;
import io.github.darkkronicle.advancedchatcore.chat.ChatMessage;
import io.github.darkkronicle.advancedchatcore.config.ConfigStorage;
import io.github.darkkronicle.advancedchatcore.gui.ContextMenu;
import io.github.darkkronicle.advancedchatcore.util.ChatHudHelper;
import io.github.darkkronicle.advancedchatcore.util.Color;
import io.github.darkkronicle.advancedchatcore.util.Colors;
import io.github.darkkronicle.advancedchatcore.util.FindType;
import io.github.darkkronicle.advancedchatcore.util.ModifierKeyUtil;
import io.github.darkkronicle.advancedchatcore.util.SearchUtils;
import io.github.darkkronicle.advancedchatlog.AdvancedChatLog;
import io.github.darkkronicle.advancedchatlog.ChatLogData;
import io.github.darkkronicle.advancedchatlog.config.ChatLogConfigStorage;
import io.github.darkkronicle.advancedchatlog.util.LogChatMessage;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import org.apache.logging.log4j.Level;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.PatternSyntaxException;

@Environment(EnvType.CLIENT)
public class ChatLogScreen extends GuiBase {

    /**
     * The px where the scroll will start
     */
    private double scrollStart = 0;

    /**
     * The px where the scroll will end
     */
    private double scrollEnd = 0;

    /**
     * The current value of scroll. This should be used to grab scroll value.
     */
    private double currentScroll = 0;

    /**
     * Last time scroll was updated. Used for smooth scroll.
     */
    private long lastScrollTime = 0;

    private ContextMenu menu = null;
    private LogChatMessage message = null;
    private LinkedHashMap<Text, ContextMenu.ContextConsumer> menuOptions = null;
    private Text hoveredMenuEntry = null;

    private List<ChatMessage.AdvancedChatLine> renderLines;
    private GuiTextFieldGeneric search = null;
    private TextFieldRunnable send = null;
    private ButtonGeneric searchType = null;
    private FindType findType = FindType.LITERAL;

    public ChatLogScreen() {
        super();
    }

    public void add(LogChatMessage message) {
        add(message.getMessage());
        if (currentScroll > 0) {
            currentScroll += message.getMessage().getLineCount() * (client.textRenderer.fontHeight + 2);
        }
    }

    public void add(ChatMessage message) {
        try {
            if (SearchUtils.isMatch(
                    message.getDisplayText().getString(), search.getText(), findType)) {
                for (int i = 0; i < message.getLineCount(); i++) {
                    renderLines.addFirst(message.getLines().get(i));
                }
            }
        } catch (PatternSyntaxException e) {
            // Already handled earlier.
        }
    }

    @Override
    public void initGui() {
        super.initGui();
        setLines(ChatLogData.getInstance().getMessages());
        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();
        search = new GuiTextFieldGeneric((width / 2) - 70, 6, 141, 20, textRenderer);
        addTextField(
                search,
                (textField -> {
                    searchText(textField.getText());
                    return true;
                })
        );
        searchType = new ButtonGeneric(width / 2 + 72, 6, 70, false, findType.getDisplayName());
        addButton(
                searchType,
                ((button, mouseButton) -> {
                    if (mouseButton == 0) {
                        findType = findType.cycle(true);
                    } else {
                        findType = findType.cycle(false);
                    }
                    button.setDisplayString(findType.getDisplayName());
                    searchText(search.getText());
                }));
        send = new TextFieldRunnable(
                2,
                height - 15,
                width - 4,
                12,
                textRenderer,
                (textFieldRunnable -> {
                    String text = textFieldRunnable.getText();
                    MutableText literal = Text.literal(text);
                    client.player.sendMessage(literal, false);
                    textFieldRunnable.setText("");
                })
        );
        addTextField(send, null);
        send.setFocused(true);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) {
            return true;
        }
        if (click.button() == 1) {
            createContextMenu((int) click.x(), (int) click.y());
            return true;
        }
        // Handle context menu clicks manually
        if (menu != null && menuOptions != null && hoveredMenuEntry != null && click.button() == 0) {
            ContextMenu.ContextConsumer consumer = menuOptions.get(hoveredMenuEntry);
            if (consumer != null) {
                consumer.takeAction(menu.getContextX(), menu.getContextY());
                menu = null;
                menuOptions = null;
                hoveredMenuEntry = null;
                return true;
            }
        }
        // Close menu on any click outside
        if (menu != null) {
            menu = null;
            menuOptions = null;
            hoveredMenuEntry = null;
        }
        if (ModifierKeyUtil.hasShiftDown()) {
            relativeScroll((int) click.y());
            return true;
        }
        Style style = getHoverStyle(click.x(), click.y());
        if (style != null && style.getClickEvent() != null) {
            net.minecraft.client.gui.screen.Screen.handleClickEvent(style.getClickEvent(), client, this);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (super.mouseDragged(click, deltaX, deltaY)) {
            return true;
        }
        if (ModifierKeyUtil.hasShiftDown()) {
            relativeScroll((int) click.y());
            return true;
        }
        return false;
    }

    public void relativeScroll(int y) {
        // Scroll click
        int height = client.getWindow().getScaledHeight() - 100;
        y -= 40;
        float percent = 1 - Math.max(0, Math.min((float) y / height, 1));
        int newPix = (int) (percent * (renderLines.size() * (textRenderer.fontHeight + 2)));
        scrollEnd = newPix;
        scrollStart = newPix;
        lastScrollTime = Util.getMeasuringTimeMs();
    }


    private void searchText(String contents) {
        if (contents.isEmpty()) {
            setLines(ChatLogData.getInstance().getMessages());
            return;
        }
        List<LogChatMessage> sorted = new ArrayList<>();
        for (LogChatMessage l : ChatLogData.getInstance().getMessages()) {
            ChatMessage m = l.getMessage();
            try {
                if (SearchUtils.isMatch(m.getDisplayText().getString(), contents, findType)) {
                    sorted.add(l);
                }
            } catch (PatternSyntaxException e) {
                sorted.clear();
                Text text = Text.literal(
                        StringUtils.translate("advancedchatlog.message.regexerror")).fillStyle(
                        Style.EMPTY.withColor(TextColor.fromFormatting(Formatting.RED)));
                text.getSiblings().add(Text.literal(" " + e.getDescription()).fillStyle(Style.EMPTY.withColor(Colors.getInstance().getColorOrWhite("gray").color())));
                ChatMessage message = ChatMessage.builder().displayText(text).originalText(text).build();
                sorted.add(new LogChatMessage(message));
                break;
            }
        }
        setLines(sorted);
    }

    private void setLines(List<LogChatMessage> messages) {
        // Don't want jank
        messages = new ArrayList<>(messages);
        if (messages.isEmpty()) {
            Text text = Text.literal(
                    StringUtils.translate("advancedchatlog.message.none")
            ).fillStyle(Style.EMPTY.withColor(TextColor.fromFormatting(Formatting.RED)));
            messages.add(new LogChatMessage(ChatMessage.builder().displayText(text).originalText(text).build()));
        }
        renderLines = new ArrayList<>();
        for (LogChatMessage l : messages) {
            ChatMessage m = l.getMessage();
            for (int i = m.getLineCount() - 1; i >= 0; i--) {
                renderLines.add(m.getLines().get(i));
            }
        }
    }

    private void updateScroll() {
        long time = Util.getMeasuringTimeMs();
        // Starting scroll + percent completed
        currentScroll = scrollStart + (
                (scrollEnd - scrollStart) * (1 - ((ConfigStorage.Easing) ChatLogConfigStorage.General.SCROLL_TYPE.config.getOptionListValue()).apply(
                        1 - ((float) time - lastScrollTime) / ChatLogConfigStorage.General.SCROLL_TIME.config.getIntegerValue()
                ))
        );
        int fontHeight = (textRenderer.fontHeight + 2);
        if (currentScroll < 0) {
            // Make sure we can still see at least one line
            currentScroll = 0;
            scrollEnd = 0;
            lastScrollTime = 0;
        }
        int maxY = fontHeight * (renderLines.size() - 1);
        if (currentScroll >= maxY) {
            // Make sure it stops at the top
            currentScroll = maxY;
            scrollEnd = maxY;
            lastScrollTime = 0;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        // Update the scroll variables
        scrollEnd = currentScroll + verticalAmount * 10 * ChatLogConfigStorage.General.SCROLL_MULTIPLIER.config.getDoubleValue();
        scrollStart = currentScroll;
        lastScrollTime = Util.getMeasuringTimeMs();
        return true;
    }

    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float partialTicks) {
        super.render(drawContext, mouseX, mouseY, partialTicks);
        updateScroll();
        int height = client.getWindow().getScaledHeight();
        int width = client.getWindow().getScaledWidth();
        int lineHeight = textRenderer.fontHeight + 2;
        // 60 px top, 40 px bottom
        int lines = (int) Math.ceil((float) (height - 70 - lineHeight) / (lineHeight));

        // Current line scrolled
        int scrollLine = (int) Math.floor((float) currentScroll / (lineHeight));

        // Offset y for scrolling. Used for partially obstructed lines.
        int y = -1 * ((int) currentScroll % lineHeight);

        // Scissor to keep boundaries for the half scroll
        double scale = client.getWindow().getScaleFactor();
        ScissorUtil.applyScissor(drawContext,
                0,
                (int) (40 * scale),
                (int) (width * scale),
                (int) ((height - 70) * scale));

        for (int i = scrollLine; i < scrollLine + lines; i++) {
            if (i >= renderLines.size()) {
                break;
            }
            ChatMessage.AdvancedChatLine line = renderLines.get(i);
            drawContext.drawTextWithShadow(textRenderer,
                    line.getText(),
                    10,
                    height - y - 40 - fontHeight,
                    Colors.getInstance().getColorOrWhite("white").color());
            y += lineHeight;
        }
        drawContext.disableScissor();
        drawContext.drawCenteredTextWithShadow(textRenderer,
                (scrollLine + 1) + "/" + renderLines.size(),
                width / 2,
                height - 28,
                Colors.getInstance().getColorOrWhite("white").color());
        drawContext.drawHoverEvent(textRenderer, getHoverStyle(mouseX, mouseY), mouseX, mouseY);
        if (menu != null) {
            // Render context menu directly - replicate ContextMenu's render logic using DrawContext
            renderContextMenuDirect(drawContext, menu, mouseX, mouseY);
        }
    }

    private void renderContextMenuDirect(DrawContext context, ContextMenu menu, int mouseX, int mouseY) {
        // Get menu properties via reflection since we can't directly access them
        try {
            java.lang.reflect.Field bgField = ContextMenu.class.getDeclaredField("background");
            bgField.setAccessible(true);
            Color background = (Color) bgField.get(menu);

            java.lang.reflect.Field hoverField = ContextMenu.class.getDeclaredField("hover");
            hoverField.setAccessible(true);
            Color hover = (Color) hoverField.get(menu);

            java.lang.reflect.Field optionsField = ContextMenu.class.getDeclaredField("options");
            optionsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            LinkedHashMap<Text, ContextMenu.ContextConsumer> options = (LinkedHashMap<Text, ContextMenu.ContextConsumer>) optionsField.get(menu);

            // Store options for click handling
            menuOptions = options;

            int x = menu.getX();
            int y = menu.getY();
            int width = menu.getWidth();
            int height = menu.getHeight();

            // Draw background
            context.fill(x, y, x + width, y + height, background.color());

            int rX = x + 2;
            int rY = y + 2;

            // Reset hovered entry
            hoveredMenuEntry = null;

            // Draw each option
            for (Text option : options.keySet()) {
                if (mouseX >= x && mouseX <= x + width && mouseY >= rY - 2 && mouseY < rY + textRenderer.fontHeight + 1) {
                    // Draw hover highlight
                    context.fill(rX - 2, rY - 2, rX - 2 + width, rY - 2 + textRenderer.fontHeight + 2, hover.color());
                    hoveredMenuEntry = option;
                }
                context.drawTextWithShadow(textRenderer, option, rX, rY, -1);
                rY += textRenderer.fontHeight + 2;
            }
        } catch (Exception e) {
            AdvancedChatLog.LOGGER.error("[ChatLogScreen] Failed to render context menu: " + e.getMessage());
        }
    }

    public void createContextMenu(int mouseX, int mouseY) {
        LinkedHashMap<Text, ContextMenu.ContextConsumer> actions = new LinkedHashMap<>();
        message = getMessage(mouseX, mouseY);
        if (message != null) {
            Text data = Text.empty();
            try {
                data.getSiblings().add(
                        Text.literal(
                                message.getMessage().getTime().format(DateTimeFormatter.ofPattern(ConfigStorage.General.TIME_FORMAT.config.getStringValue()))
                        ).fillStyle(Style.EMPTY.withFormatting(Formatting.AQUA))
                );
            } catch (IllegalArgumentException e) {
                AdvancedChatLog.LOGGER.log(Level.WARN, "Can't format time for context menu!", e);
            }
            if (message.getMessage().getOwner() != null) {
                data.getSiblings().add(Text.literal(" - ").fillStyle(Style.EMPTY.withFormatting(Formatting.GRAY)));
                if (message.getMessage().getOwner().getEntry().getDisplayName() != null) {
                    data.getSiblings().add(message.getMessage().getOwner().getEntry().getDisplayName());
                } else {
                    data.getSiblings().add(Text.literal(message.getMessage().getOwner().getEntry().getProfile().name()));
                }
            }
            if (!data.getString().isBlank()) {
                actions.put(data, (x, y) -> {
                });
            }
            actions.put(Text.literal(StringUtils.translate("advancedchatlog.context.copy")), (x, y) -> {
                MinecraftClient.getInstance().keyboard.setClipboard(message.getMessage().getOriginalText().getString());
                InfoUtils.printActionbarMessage("advancedchatlog.context.copied");
            });
        }
        actions.put(Text.literal(StringUtils.translate("advancedchatlog.context.clearallmessages")), (x, y) -> {
            ChatLogData.getInstance().clear();
            setLines(ChatLogData.getInstance().getMessages());
        });
        menu = new ContextMenu(mouseX, mouseY, actions, () -> menu = null);
    }

    public Style getHoverStyle(double mouseX, double mouseY) {
        int lineHeight = textRenderer.fontHeight + 2;
        int lines = (int) Math.ceil((float) (height - 70 - lineHeight) / (lineHeight));

        // Current line scrolled
        int scrollLine = (int) Math.floor((float) currentScroll / (lineHeight));

        // Offset y for scrolling. Used for partially obstructed lines.
        int y = -1 * ((int) currentScroll % lineHeight);
        int height = client.getWindow().getScaledHeight();
        // Change the perspective of mouseY from where the text started.
        mouseY = height - mouseY - 40;
        mouseX = mouseX - 10;

        for (int i = scrollLine; i < scrollLine + lines; i++) {
            if (i >= renderLines.size()) {
                break;
            }
            if (y <= mouseY && y + lineHeight >= mouseY) {
                ChatMessage.AdvancedChatLine line = renderLines.get(i);
                // Use a visitor pattern to extract style from OrderedText at the mouse position
                int targetX = (int) mouseX;
                int[] currentX = {0};
                StyleHolder styleHolder = new StyleHolder();

                line.getText().asOrderedText().accept((index, style, codePoint) -> {
                    String charString = new String(Character.toChars(codePoint));
                    int width = textRenderer.getWidth(charString);
                    if (currentX[0] <= targetX && targetX < currentX[0] + width) {
                        styleHolder.style = style;
                        return false; // Stop iteration
                    }
                    currentX[0] += width;
                    return true; // Continue iteration
                });

                return styleHolder.style;
            }
            y += lineHeight;
        }
        return null;
    }

    /**
     * Helper class to hold a style reference from inside a lambda
     */
    private static class StyleHolder {
        Style style = null;
    }

    public LogChatMessage getMessage(double mouseX, double mouseY) {
        int lineHeight = textRenderer.fontHeight + 2;
        int lines = (int) Math.ceil((float) (height - 70 - lineHeight) / (lineHeight));

        // Current line scrolled
        int scrollLine = (int) Math.floor((float) currentScroll / (lineHeight));

        // Offset y for scrolling. Used for partially obstructed lines.
        int y = -1 * ((int) currentScroll % lineHeight);
        int height = client.getWindow().getScaledHeight();
        // Change the perspective of mouseY from where the text started.
        mouseY = height - mouseY - 40;

        for (int i = scrollLine; i < scrollLine + lines; i++) {
            if (i >= renderLines.size()) {
                break;
            }
            if (y <= mouseY && y + lineHeight >= mouseY) {
                ChatMessage.AdvancedChatLine line = renderLines.get(i);
                return ChatLogData.getInstance().getLogMessage(line.getParent());
            }
            y += lineHeight;
        }
        return null;
    }


}
