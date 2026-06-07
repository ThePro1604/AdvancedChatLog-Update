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
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.ChatFormatting;
import net.minecraft.util.TimeUtil;
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
    private LinkedHashMap<Component, ContextMenu.ContextConsumer> menuOptions = null;
    private Component hoveredMenuEntry = null;

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
            currentScroll += message.getMessage().getLineCount() * (Minecraft.getInstance().font.lineHeight + 2);
        }
    }

    public void add(ChatMessage message) {
        try {
            if (SearchUtils.isMatch(
                    message.getDisplayText().getString(), search.getValue(), findType)) {
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
        int width = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int height = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        search = new GuiTextFieldGeneric((width / 2) - 70, 6, 141, 20, font);
        addTextField(
                search,
                (textField -> {
                    searchText(textField.getValue());
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
                    searchText(search.getValue());
                }));
        send = new TextFieldRunnable(
                2,
                height - 15,
                width - 4,
                12,
                font,
                (textFieldRunnable -> {
                    String text = textFieldRunnable.getValue();
                    MutableComponent literal = Component.literal(text);
                    Minecraft.getInstance().player.sendSystemMessage(literal);
                    textFieldRunnable.setValue("");
                })
        );
        addTextField(send, null);
        send.setFocused(true);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
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
            handleClickEvent(style.getClickEvent());
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        if (super.mouseDragged(click, deltaX, deltaY)) {
            return true;
        }
        if (ModifierKeyUtil.hasShiftDown()) {
            relativeScroll((int) click.y());
            return true;
        }
        return false;
    }

    private void handleClickEvent(ClickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event instanceof ClickEvent.RunCommand cmd) {
            String command = cmd.command();
            if (command.startsWith("/")) command = command.substring(1);
            if (mc.player != null && mc.player.connection != null) {
                mc.player.connection.sendCommand(command);
            }
            GuiBase.openGui(null);
        } else if (event instanceof ClickEvent.OpenUrl openUrl) {
            net.minecraft.util.Util.getPlatform().openUri(openUrl.uri());
        } else if (event instanceof ClickEvent.SuggestCommand suggest) {
            GuiBase.openGui(null);
            mc.setScreen(new io.github.darkkronicle.advancedchatcore.chat.AdvancedChatScreen(suggest.command()));
        } else if (event instanceof ClickEvent.CopyToClipboard copy) {
            mc.keyboardHandler.setClipboard(copy.value());
        }
    }

    public void relativeScroll(int y) {
        // Scroll click
        int height = Minecraft.getInstance().getWindow().getGuiScaledHeight() - 100;
        y -= 40;
        float percent = 1 - Math.max(0, Math.min((float) y / height, 1));
        int newPix = (int) (percent * (renderLines.size() * (font.lineHeight + 2)));
        scrollEnd = newPix;
        scrollStart = newPix;
        lastScrollTime = System.currentTimeMillis();
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
                Component text = Component.literal(
                        StringUtils.translate("advancedchatlog.message.regexerror")).withStyle(
                        Style.EMPTY.withColor(TextColor.fromLegacyFormat(ChatFormatting.RED)));
                text.getSiblings().add(Component.literal(" " + e.getDescription()).withStyle(Style.EMPTY.withColor(Colors.getInstance().getColorOrWhite("gray").color())));
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
            Component text = Component.literal(
                    StringUtils.translate("advancedchatlog.message.none")
            ).withStyle(Style.EMPTY.withColor(TextColor.fromLegacyFormat(ChatFormatting.RED)));
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
        long time = System.currentTimeMillis();
        // Starting scroll + percent completed
        currentScroll = scrollStart + (
                (scrollEnd - scrollStart) * (1 - ((ConfigStorage.Easing) ChatLogConfigStorage.General.SCROLL_TYPE.config.getOptionListValue()).apply(
                        1 - ((float) time - lastScrollTime) / ChatLogConfigStorage.General.SCROLL_TIME.config.getIntegerValue()
                ))
        );
        int fontHeight = (font.lineHeight + 2);
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
        lastScrollTime = System.currentTimeMillis();
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor drawContext, int mouseX, int mouseY, float partialTicks) {
        super.extractRenderState(drawContext, mouseX, mouseY, partialTicks);
        updateScroll();
        int height = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int width = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int lineHeight = font.lineHeight + 2;
        // 60 px top, 40 px bottom
        int lines = (int) Math.ceil((float) (height - 70 - lineHeight) / (lineHeight));

        // Current line scrolled
        int scrollLine = (int) Math.floor((float) currentScroll / (lineHeight));

        // Offset y for scrolling. Used for partially obstructed lines.
        int y = -1 * ((int) currentScroll % lineHeight);

        // Scissor to keep boundaries for the half scroll
        double scale = Minecraft.getInstance().getWindow().getGuiScale();
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
            drawContext.text(font,
                    line.getText(),
                    10,
                    height - y - 40 - fontHeight,
                    Colors.getInstance().getColorOrWhite("white").color());
            y += lineHeight;
        }
        drawContext.disableScissor();
        drawContext.text(font,
                (scrollLine + 1) + "/" + renderLines.size(),
                width / 2,
                height - 28,
                Colors.getInstance().getColorOrWhite("white").color());
        Style hoverStyle = getHoverStyle(mouseX, mouseY);
        if (hoverStyle != null && hoverStyle.getHoverEvent() != null) {
            ChatHudHelper.renderHoverTooltip(drawContext, hoverStyle, mouseX, mouseY);
        }
        if (menu != null) {
            // Render context menu directly - replicate ContextMenu's render logic using GuiGraphicsExtractor
            renderContextMenuDirect(drawContext, menu, mouseX, mouseY);
        }
    }

    private void renderContextMenuDirect(GuiGraphicsExtractor context, ContextMenu menu, int mouseX, int mouseY) {
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
            LinkedHashMap<Component, ContextMenu.ContextConsumer> options = (LinkedHashMap<Component, ContextMenu.ContextConsumer>) optionsField.get(menu);

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
            for (Component option : options.keySet()) {
                if (mouseX >= x && mouseX <= x + width && mouseY >= rY - 2 && mouseY < rY + font.lineHeight + 1) {
                    // Draw hover highlight
                    context.fill(rX - 2, rY - 2, rX - 2 + width, rY - 2 + font.lineHeight + 2, hover.color());
                    hoveredMenuEntry = option;
                }
                context.text(font, option, rX, rY, -1);
                rY += font.lineHeight + 2;
            }
        } catch (Exception e) {
            AdvancedChatLog.LOGGER.error("[ChatLogScreen] Failed to render context menu: " + e.getMessage());
        }
    }

    public void createContextMenu(int mouseX, int mouseY) {
        LinkedHashMap<Component, ContextMenu.ContextConsumer> actions = new LinkedHashMap<>();
        message = getMessage(mouseX, mouseY);
        if (message != null) {
            Component data = Component.empty();
            try {
                data.getSiblings().add(
                        Component.literal(
                                message.getMessage().getTime().format(DateTimeFormatter.ofPattern(ConfigStorage.General.TIME_FORMAT.config.getStringValue()))
                        ).withStyle(Style.EMPTY.applyFormat(ChatFormatting.AQUA))
                );
            } catch (IllegalArgumentException e) {
                AdvancedChatLog.LOGGER.log(Level.WARN, "Can't format time for context menu!", e);
            }
            if (message.getMessage().getOwner() != null) {
                data.getSiblings().add(Component.literal(" - ").withStyle(Style.EMPTY.applyFormat(ChatFormatting.GRAY)));
                if (message.getMessage().getOwner().getEntry().getTabListDisplayName() != null) {
                    data.getSiblings().add(message.getMessage().getOwner().getEntry().getTabListDisplayName());
                } else {
                    data.getSiblings().add(Component.literal(message.getMessage().getOwner().getEntry().getProfile().name()));
                }
            }
            if (!data.getString().isBlank()) {
                actions.put(data, (x, y) -> {
                });
            }
            actions.put(Component.literal(StringUtils.translate("advancedchatlog.context.copy")), (x, y) -> {
                Minecraft.getInstance().keyboardHandler.setClipboard(message.getMessage().getOriginalText().getString());
                InfoUtils.printActionbarMessage("advancedchatlog.context.copied");
            });
        }
        actions.put(Component.literal(StringUtils.translate("advancedchatlog.context.clearallmessages")), (x, y) -> {
            ChatLogData.getInstance().clear();
            setLines(ChatLogData.getInstance().getMessages());
        });
        menu = new ContextMenu(mouseX, mouseY, actions, () -> menu = null);
    }

    public Style getHoverStyle(double mouseX, double mouseY) {
        int lineHeight = font.lineHeight + 2;
        int lines = (int) Math.ceil((float) (height - 70 - lineHeight) / (lineHeight));

        // Current line scrolled
        int scrollLine = (int) Math.floor((float) currentScroll / (lineHeight));

        // Offset y for scrolling. Used for partially obstructed lines.
        int y = -1 * ((int) currentScroll % lineHeight);
        int height = Minecraft.getInstance().getWindow().getGuiScaledHeight();
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

                line.getText().getVisualOrderText().accept((index, style, codePoint) -> {
                    String charString = new String(Character.toChars(codePoint));
                    int width = font.width(charString);
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
        int lineHeight = font.lineHeight + 2;
        int lines = (int) Math.ceil((float) (height - 70 - lineHeight) / (lineHeight));

        // Current line scrolled
        int scrollLine = (int) Math.floor((float) currentScroll / (lineHeight));

        // Offset y for scrolling. Used for partially obstructed lines.
        int y = -1 * ((int) currentScroll % lineHeight);
        int height = Minecraft.getInstance().getWindow().getGuiScaledHeight();
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
