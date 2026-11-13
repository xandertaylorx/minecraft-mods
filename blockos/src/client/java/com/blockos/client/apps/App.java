package com.blockos.client.apps;

import com.blockos.client.screen.ComputerScreen;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

/** One BlockOS app. Coordinates are GUI pixels; (x, y, w, h) is the app's area inside its window. */
public abstract class App {
	protected final ComputerScreen os;
	protected int x, y, w, h;

	protected App(ComputerScreen os) {
		this.os = os;
	}

	public abstract String title();

	/** Icon drawn on the desktop (16x16 area at ix, iy). */
	public abstract void drawIcon(GuiGraphicsExtractor g, int ix, int iy);

	public final void layout(int x, int y, int w, int h) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
	}

	public abstract void render(GuiGraphicsExtractor g, int mouseX, int mouseY);

	public boolean keyPressed(KeyEvent e) {
		return false;
	}

	public boolean charTyped(CharacterEvent e) {
		return false;
	}

	public boolean mouseClicked(double mx, double my, int button, boolean doubleClick) {
		return false;
	}

	public boolean mouseDragged(double mx, double my, int button) {
		return false;
	}

	public void mouseReleased() {
	}

	public boolean mouseScrolled(double amount) {
		return false;
	}

	public void tick() {
	}

	/** Called when fresh data arrived from the computer. */
	public void onSync() {
	}

	// ---- helpers ----

	protected Font font() {
		return this.os.font();
	}

	protected boolean inside(double mx, double my, int bx, int by, int bw, int bh) {
		return mx >= bx && mx < bx + bw && my >= by && my < by + bh;
	}

	/** A flat button; returns nothing, check clicks with inside(). */
	protected void button(GuiGraphicsExtractor g, int bx, int by, int bw, int bh, String label, int mouseX, int mouseY) {
		boolean hover = this.inside(mouseX, mouseY, bx, by, bw, bh);
		g.fill(bx, by, bx + bw, by + bh, hover ? this.os.theme().accent() : this.os.theme().panel());
		g.outline(bx, by, bw, bh, this.os.theme().accentDim());
		int tw = this.font().width(label);
		g.text(this.font(), label, bx + (bw - tw) / 2, by + (bh - 8) / 2, hover ? 0xFF101010 : this.os.theme().text(), false);
	}
}
