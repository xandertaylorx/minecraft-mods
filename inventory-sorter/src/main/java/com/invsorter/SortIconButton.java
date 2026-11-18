package com.invsorter;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/** A small square icon button: either the "sort" icon (bars) or the "quick stack" icon (arrow into a chest). */
public class SortIconButton extends AbstractButton {
    public static final int SIZE = 11;

    public enum Icon { SORT, QUICK_STACK }

    private final Runnable action;
    private final Icon icon;

    public SortIconButton(Icon icon, Component tooltip, Runnable action) {
        super(0, 0, SIZE, SIZE, tooltip);
        this.icon = icon;
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
        int c = isHoveredOrFocused() ? 0xFFFFFFA0 : 0xFFE6E6E6;
        int x = getX() + 2;
        int y = getY() + 2;
        if (icon == Icon.SORT) {
            g.fill(x, y + 1, x + 7, y + 2, c);      // long bar
            g.fill(x, y + 3, x + 5, y + 4, c);      // medium bar
            g.fill(x, y + 5, x + 3, y + 6, c);      // short bar
        } else {
            g.fill(x + 3, y, x + 4, y + 3, c);      // arrow stem
            g.fill(x + 1, y + 2, x + 6, y + 3, c);  // arrow head
            g.fill(x + 2, y + 3, x + 5, y + 4, c);
            g.fill(x + 3, y + 4, x + 4, y + 5, c);
            g.fill(x, y + 5, x + 1, y + 7, c);      // chest: left, right, bottom
            g.fill(x + 6, y + 5, x + 7, y + 7, c);
            g.fill(x, y + 6, x + 7, y + 7, c);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
