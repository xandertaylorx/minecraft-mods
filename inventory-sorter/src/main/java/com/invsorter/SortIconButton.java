package com.invsorter;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/** A small square button that draws a "sort" icon (three bars, long to short) instead of a text label. */
public class SortIconButton extends AbstractButton {
    public static final int SIZE = 14;

    private final Runnable action;

    public SortIconButton(Component tooltip, Runnable action) {
        super(0, 0, SIZE, SIZE, tooltip);
        this.action = action;
        setTooltip(Tooltip.create(tooltip));
    }

    @Override
    public void onPress(InputWithModifiers input) {
        action.run();
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        extractDefaultSprite(g);
        int color = isHoveredOrFocused() ? 0xFFFFFFA0 : 0xFFE6E6E6;
        int x = getX() + 3;
        int y = getY() + 3;
        g.fill(x, y, x + 8, y + 2, color);          // long bar
        g.fill(x, y + 3, x + 6, y + 5, color);      // medium bar
        g.fill(x, y + 6, x + 4, y + 8, color);      // short bar
        g.fill(x + 8, y + 5, x + 9, y + 8, color);  // little "descending" arrow stem
        g.fill(x + 7, y + 7, x + 10, y + 8, color);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
