/*
 * Copyright (C) 2021-2026 DarkKronicle
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.github.darkkronicle.advancedchatlog.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;

@Environment(EnvType.CLIENT)
public class ScissorUtil {

    private ScissorUtil() {
    }

    public static void applyScissor(GuiGraphicsExtractor drawContext, int x1, int y1, int x2, int y2) {
        drawContext.enableScissor(x1, y1, x2 - x1, y2 - y1);
    }

    public static void resetScissor(GuiGraphicsExtractor drawContext) {
        drawContext.disableScissor();
    }
}
