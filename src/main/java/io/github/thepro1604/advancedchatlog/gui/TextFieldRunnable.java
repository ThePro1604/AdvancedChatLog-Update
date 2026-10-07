/*
 * Copyright (C) 2021-2026 thepro1604
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.github.thepro1604.advancedchatlog.gui;

import com.mojang.blaze3d.platform.InputConstants;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.KeyEvent;

public class TextFieldRunnable extends GuiTextFieldGeneric {

    private final Consumer<TextFieldRunnable> onApply;

    public TextFieldRunnable(
            int x,
            int y,
            int width,
            int height,
            Font font,
            Consumer<TextFieldRunnable> onApply) {
        super(x, y, width, height, font);
        this.onApply = onApply;
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (super.keyPressed(input)) {
            return true;
        }
        if (input.key() == InputConstants.KEY_RETURN || input.key() == InputConstants.KEY_NUMPADENTER) {
            onApply.accept(this);
            return true;
        }
        return false;
    }
}
