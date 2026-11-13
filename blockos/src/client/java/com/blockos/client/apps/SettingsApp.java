package com.blockos.client.apps;

import com.blockos.client.screen.ComputerScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

/** Rename the computer, pick a color theme and wallpaper, see what's going on. */
public class SettingsApp extends App {
	private static final String[] WALLPAPERS = {"grid", "sunset", "matrix"};
	private final StringBuilder name = new StringBuilder();
	private boolean editingName;

	public SettingsApp(ComputerScreen os) {
		super(os);
	}

	@Override
	public String title() {
		return "Settings";
	}

	@Override
	public void drawIcon(GuiGraphicsExtractor g, int ix, int iy) {
		int c = 0xFFB8C4D6;
		g.fill(ix + 5, iy + 2, ix + 11, iy + 14, c);
		g.fill(ix + 2, iy + 5, ix + 14, iy + 11, c);
		g.fill(ix + 3, iy + 3, ix + 13, iy + 13, c);
		g.fill(ix + 6, iy + 6, ix + 10, iy + 10, 0xFF1B1F24);
	}

	@Override
	public void render(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		ComputerScreen.Theme t = this.os.theme();
		int yy = this.y + 4;
		g.text(this.font(), "Computer name", this.x + 6, yy, 0xA0FFFFFF, false);
		yy += 11;
		g.fill(this.x + 6, yy, this.x + 170, yy + 13, 0xFF151A1F);
		g.outline(this.x + 6, yy, 164, 13, this.editingName ? t.accent() : t.accentDim());
		g.text(this.font(), this.editingName ? this.name + "_" : this.os.label(), this.x + 10, yy + 3, t.text(), false);
		this.button(g, this.x + 176, yy, 44, 13, this.editingName ? "Save" : "Edit", mouseX, mouseY);
		yy += 22;

		g.text(this.font(), "Color theme", this.x + 6, yy, 0xA0FFFFFF, false);
		yy += 11;
		for (int i = 0; i < ComputerScreen.THEMES.size(); i++) {
			ComputerScreen.Theme th = ComputerScreen.THEMES.get(i);
			int bx = this.x + 6 + i * 50;
			g.fill(bx, yy, bx + 46, yy + 16, th.panel());
			g.fill(bx + 2, yy + 2, bx + 12, yy + 14, th.accent());
			g.text(this.font(), th.name(), bx + 15, yy + 4, th.text(), false);
			if (th.name().equals(t.name())) g.outline(bx - 1, yy - 1, 48, 18, 0xFFFFFFFF);
		}
		yy += 26;

		g.text(this.font(), "Wallpaper", this.x + 6, yy, 0xA0FFFFFF, false);
		yy += 11;
		for (int i = 0; i < WALLPAPERS.length; i++) {
			int bx = this.x + 6 + i * 58;
			boolean sel = WALLPAPERS[i].equals(this.os.wallpaper());
			g.fill(bx, yy, bx + 54, yy + 14, sel ? t.accent() : t.panel());
			g.text(this.font(), WALLPAPERS[i], bx + 6, yy + 3, sel ? 0xFF101010 : t.text(), false);
		}
		yy += 24;

		int used = 0;
		for (var e : this.os.files().entrySet()) used += e.getKey().length() + e.getValue().length();
		String[] info = {
			"Storage: " + used / 1024 + " KB of 64 KB",
			"Program running: " + (this.os.running().isEmpty() ? "none" : this.os.running()),
			"Wireless: " + (this.os.channel() < 0 ? "off" : "listening on channel " + this.os.channel()),
			"Location: " + this.os.pos.toShortString(),
			"BlockOS 1.0"};
		for (String s : info) {
			g.text(this.font(), s, this.x + 6, yy, t.text(), false);
			yy += 10;
		}
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button, boolean doubleClick) {
		int yy = this.y + 15;
		if (this.inside(mx, my, this.x + 6, yy, 164, 13) || (this.inside(mx, my, this.x + 176, yy, 44, 13) && !this.editingName)) {
			this.editingName = true;
			this.name.setLength(0);
			this.name.append(this.os.label());
			return true;
		}
		if (this.inside(mx, my, this.x + 176, yy, 44, 13) && this.editingName) {
			this.commitName();
			return true;
		}
		yy += 22 + 11;
		for (int i = 0; i < ComputerScreen.THEMES.size(); i++) {
			if (this.inside(mx, my, this.x + 6 + i * 50, yy, 46, 16)) this.os.saveFile(".theme", ComputerScreen.THEMES.get(i).name());
		}
		yy += 26 + 11;
		for (int i = 0; i < WALLPAPERS.length; i++) {
			if (this.inside(mx, my, this.x + 6 + i * 58, yy, 54, 14)) this.os.saveFile(".wallpaper", WALLPAPERS[i]);
		}
		return true;
	}

	private void commitName() {
		this.editingName = false;
		String n = this.name.toString().trim();
		if (!n.isEmpty()) this.os.runCommand("label " + n);
	}

	@Override
	public boolean charTyped(CharacterEvent e) {
		if (this.editingName && e.isAllowedChatCharacter() && this.name.length() < 32) this.name.append(e.codepointAsString());
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (!this.editingName) return false;
		if (e.key() == InputConstants.KEY_BACKSPACE && !this.name.isEmpty()) this.name.setLength(this.name.length() - 1);
		if (e.key() == InputConstants.KEY_RETURN) this.commitName();
		return true;
	}
}
