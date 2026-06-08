/*
 * Copyright (C) 2021-2026 thepro1604
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.github.thepro1604.advancedchatlog.gui;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.util.StringUtils;
import io.github.thepro1604.advancedchatcore.chat.AdvancedChatScreen;
import io.github.thepro1604.advancedchatcore.config.ConfigStorage;
import io.github.thepro1604.advancedchatcore.gui.CleanButton;
import io.github.thepro1604.advancedchatcore.gui.IconButton;
import io.github.thepro1604.advancedchatcore.interfaces.AdvancedChatScreenSection;
import io.github.thepro1604.advancedchatcore.util.Color;
import io.github.thepro1604.advancedchatlog.AdvancedChatLog;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

@Environment(EnvType.CLIENT)
public class ChatLogScreenSection extends AdvancedChatScreenSection {

    private final static Identifier LOG_ICON = Identifier.fromNamespaceAndPath(AdvancedChatLog.MOD_ID, "log");

    public ChatLogScreenSection(AdvancedChatScreen screen) {
        super(screen);
    }

    @Override
    public void initGui() {
        getScreen().getRightSideButtons().add(
                "settings",
                new IconButton(0, 0, 14, 32, LOG_ICON,
                        (button) -> GuiBase.openGui(new ChatLogScreen())
                )
        );
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {}
}