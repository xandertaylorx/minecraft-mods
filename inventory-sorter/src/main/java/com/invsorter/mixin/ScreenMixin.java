package com.invsorter.mixin;

import com.invsorter.InventorySorterClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While quick stack is working through chests, don't draw the chest screens it opens. */
@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("HEAD"), cancellable = true)
    private void invsorter$hide(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        if (InventorySorterClient.hideChestScreens()
                && (Object) this instanceof AbstractContainerScreen<?> screen
                && InventorySorterClient.isStackMenu(screen.getMenu())) {
            ci.cancel();
        }
    }
}
