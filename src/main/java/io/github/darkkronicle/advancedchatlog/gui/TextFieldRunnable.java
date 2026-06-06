/*
 * Copyright (C) 2021-2026 DarkKronicle
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.github.darkkronicle.advancedchatlog.gui;

import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

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
        if (input.key() == GLFW.GLFW_KEY_ENTER) {
            onApply.accept(this);
            return true;
        }
        return false;
    }
}
