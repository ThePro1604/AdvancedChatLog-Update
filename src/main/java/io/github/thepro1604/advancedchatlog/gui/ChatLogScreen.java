/*
 * Copyright (C) 2021-2026 thepro1604
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.github.thepro1604.advancedchatlog.gui;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiScrollBar;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.malilib.util.StringUtils;
import io.github.thepro1604.advancedchatcore.chat.ChatMessage;
import io.github.thepro1604.advancedchatcore.config.ConfigStorage;
import io.github.thepro1604.advancedchatcore.gui.ContextMenu;
import io.github.thepro1604.advancedchatcore.util.ChatHudHelper;
import io.github.thepro1604.advancedchatcore.util.Color;
import io.github.thepro1604.advancedchatcore.util.Colors;
import io.github.thepro1604.advancedchatcore.util.FindType;
import io.github.thepro1604.advancedchatcore.util.SearchUtils;
import io.github.thepro1604.advancedchatlog.AdvancedChatLog;
import io.github.thepro1604.advancedchatlog.ChatLogData;
import io.github.thepro1604.advancedchatlog.config.ChatLogConfigStorage;
import io.github.thepro1604.advancedchatlog.util.LogChatMessage;
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
     * Tracks the scroll position in pixels, the same way malilib's own scrollable list widgets
     * (e.g. {@code WidgetListBase}, used by the settings menu) do. Its value is clamped to
     * {@code [0, maxValue]} internally, and dragging/scrolling both go through it.
     */
    private final GuiScrollBar scrollBar = new GuiScrollBar();

    private ContextMenu menu = null;
    private LogChatMessage message = null;
    private LinkedHashMap<Component, ContextMenu.ContextConsumer> menuOptions = null;
    private Component hoveredMenuEntry = null;

    private List<ChatMessage.AdvancedChatLine> renderLines;
    private GuiTextFieldGeneric search = null;
    private TextFieldRunnable send = null;
    private ButtonGeneric searchType = null;
    private ButtonGeneric matchMode = null;
    private FindType findType =
            (FindType) ChatLogConfigStorage.General.DEFAULT_FIND_TYPE.config.getOptionListValue();

    /**
     * Controls whether the search box contents get split into multiple comma-separated terms, and
     * if so, how those terms are combined. Defaults to {@link MultiSearchMode#OFF} so a search
     * containing a literal comma behaves exactly as it did before this feature existed.
     */
    private MultiSearchMode multiSearchMode = MultiSearchMode.OFF;

    /** The different ways the search box contents can be interpreted. */
    private enum MultiSearchMode {
        /** The whole search box is treated as a single term; commas are matched literally. */
        OFF,
        /** The search box is split on commas; a message matches if it matches any one term. */
        ANY,
        /** The search box is split on commas; a message must match every term. */
        ALL;

        String getDisplayName() {
            return StringUtils.translate("advancedchatlog.search.multi." + name().toLowerCase());
        }

        MultiSearchMode cycle(boolean forward) {
            MultiSearchMode[] values = values();
            int id = this.ordinal() + (forward ? 1 : -1);
            if (id >= values.length) {
                id = 0;
            } else if (id < 0) {
                id = values.length - 1;
            }
            return values[id];
        }
    }

    public ChatLogScreen() {
        super();
    }

    public void add(LogChatMessage message) {
        add(message.getMessage());
        if (scrollBar.getValue() > 0) {
            // Keep whatever the user is currently looking at in view instead of letting it shift
            // when a new message pushes everything else back by one.
            scrollBar.offsetValue(message.getMessage().getLineCount() * (Minecraft.getInstance().font.lineHeight + 2));
        }
    }

    public void add(ChatMessage message) {
        try {
            if (matchesSearch(message.getDisplayText().getString(), search.getValue())) {
                for (int i = 0; i < message.getLineCount(); i++) {
                    renderLines.addFirst(message.getLines().get(i));
                }
            }
        } catch (PatternSyntaxException e) {
            // Already handled earlier.
        }
    }

    /**
     * Splits the raw search box contents into individual search terms.
     *
     * <p>Terms are separated by commas so users can search for e.g. "cake, cookie" at once, but
     * only when {@link #multiSearchMode} is not {@link MultiSearchMode#OFF} - otherwise the whole
     * search box is kept as-is so a literal comma in the search can still be matched. Splitting is
     * also skipped for {@link FindType#REGEX} and {@link FindType#CUSTOM} since a comma can be
     * meaningful syntax there (e.g. the {@code {1,3}} quantifier).
     */
    private List<String> splitSearchTerms(String contents) {
        if (contents.isEmpty()) {
            return List.of();
        }
        if (multiSearchMode == MultiSearchMode.OFF
                || findType == FindType.REGEX
                || findType == FindType.CUSTOM) {
            return List.of(contents);
        }
        List<String> terms = new ArrayList<>();
        for (String part : contents.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                terms.add(trimmed);
            }
        }
        if (terms.isEmpty()) {
            terms.add(contents.trim());
        }
        return terms;
    }

    /**
     * Checks whether {@code text} matches the search box contents, combining multiple
     * comma-separated terms with either AND ({@link MultiSearchMode#ALL}) or OR
     * ({@link MultiSearchMode#ANY}) semantics. With {@link MultiSearchMode#OFF} there is only ever
     * a single term, so the AND/OR distinction has no effect.
     */
    private boolean matchesSearch(String text, String contents) {
        List<String> terms = splitSearchTerms(contents);
        if (terms.isEmpty()) {
            return true;
        }
        if (multiSearchMode == MultiSearchMode.ALL) {
            for (String term : terms) {
                if (!SearchUtils.isMatch(text, term, findType)) {
                    return false;
                }
            }
            return true;
        }
        for (String term : terms) {
            if (SearchUtils.isMatch(text, term, findType)) {
                return true;
            }
        }
        return false;
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
        matchMode = new ButtonGeneric(width / 2 - 142, 6, 70, false, multiSearchMode.getDisplayName());
        matchMode.setHoverStrings(
                StringUtils.translate("advancedchatlog.search.multi.hover.title"),
                StringUtils.translate("advancedchatlog.search.multi.hover.usage"),
                StringUtils.translate("advancedchatlog.search.multi.hover.off"),
                StringUtils.translate("advancedchatlog.search.multi.hover.any"),
                StringUtils.translate("advancedchatlog.search.multi.hover.all"),
                StringUtils.translate("advancedchatlog.search.multi.hover.regexnote"));
        addButton(
                matchMode,
                ((button, mouseButton) -> {
                    if (mouseButton == 0) {
                        multiSearchMode = multiSearchMode.cycle(true);
                    } else {
                        multiSearchMode = multiSearchMode.cycle(false);
                    }
                    button.setDisplayString(multiSearchMode.getDisplayName());
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
        Style style = getHoverStyle(click.x(), click.y());
        if (style != null && style.getClickEvent() != null) {
            handleClickEvent(style.getClickEvent());
            return true;
        }
        return false;
    }

    @Override
    public boolean onMouseClicked(MouseButtonEvent click, boolean doubled) {
        // Same pattern the settings menu's scroll list uses: a click that lands on the scrollbar
        // thumb starts a drag. GuiBase.mouseClicked already calls this hook virtually via the
        // super.mouseClicked(...) call at the top of mouseClicked(...) above.
        if (click.button() == 0 && scrollBar.wasMouseOver()) {
            scrollBar.setIsDragging(true);
            return true;
        }
        return super.onMouseClicked(click, doubled);
    }

    @Override
    public boolean onMouseReleased(MouseButtonEvent click) {
        if (click.button() == 0) {
            scrollBar.setIsDragging(false);
        }
        return super.onMouseReleased(click);
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
            mc.setScreenAndShow(new io.github.thepro1604.advancedchatcore.chat.AdvancedChatScreen(suggest.command()));
        } else if (event instanceof ClickEvent.CopyToClipboard copy) {
            mc.keyboardHandler.setClipboard(copy.value());
        }
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
                if (matchesSearch(m.getDisplayText().getString(), contents)) {
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

    @Override
    public boolean onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Plug into GuiBase's own onMouseScrolled hook instead of overriding the top-level
        // mouseScrolled(...) - this is the same extension point malilib's own scrollable list
        // widgets (e.g. the settings menu, via GuiListBase/WidgetListBase) use, so it goes through
        // GuiBase's delta accumulation/rounding and its buttons/text-field consumption check first.
        if (super.onMouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        scrollBar.offsetValue((int) Math.round(
                verticalAmount * 10 * ChatLogConfigStorage.General.SCROLL_MULTIPLIER.config.getDoubleValue()));
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor drawContext, int mouseX, int mouseY, float partialTicks) {
        super.extractRenderState(drawContext, mouseX, mouseY, partialTicks);
        int height = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int width = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int lineHeight = font.lineHeight + 2;
        // 60 px top, 40 px bottom
        int lines = (int) Math.ceil((float) (height - 70 - lineHeight) / (lineHeight));

        int maxScroll = lineHeight * (renderLines.size() - 1);
        scrollBar.setMaxValue(maxScroll);
        int currentScroll = scrollBar.getValue();

        // Current line scrolled
        int scrollLine = currentScroll / lineHeight;

        // Offset y for scrolling. Used for partially obstructed lines.
        int y = -1 * (currentScroll % lineHeight);

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

        // Render the same scrollbar widget the settings menu uses, along the right edge of the
        // visible chat area.
        scrollBar.render(GuiContext.fromGuiGraphics(drawContext), mouseX, mouseY, partialTicks,
                width - 9, 40, 8, height - 70, maxScroll);

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
        int currentScroll = scrollBar.getValue();
        int scrollLine = currentScroll / lineHeight;

        // Offset y for scrolling. Used for partially obstructed lines.
        int y = -1 * (currentScroll % lineHeight);
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
        int currentScroll = scrollBar.getValue();
        int scrollLine = currentScroll / lineHeight;

        // Offset y for scrolling. Used for partially obstructed lines.
        int y = -1 * (currentScroll % lineHeight);
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
