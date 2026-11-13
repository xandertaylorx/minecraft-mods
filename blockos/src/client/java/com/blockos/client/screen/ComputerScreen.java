package com.blockos.client.screen;

import com.blockos.client.apps.App;
import com.blockos.client.apps.CalculatorApp;
import com.blockos.client.apps.ClockApp;
import com.blockos.client.apps.FilesApp;
import com.blockos.client.apps.MessengerApp;
import com.blockos.client.apps.NotepadApp;
import com.blockos.client.apps.PaintApp;
import com.blockos.client.apps.SettingsApp;
import com.blockos.client.apps.SnakeApp;
import com.blockos.client.apps.TerminalApp;
import com.blockos.net.Net;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.platform.InputConstants;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The BlockOS desktop: a monitor with a wallpaper, app icons, a taskbar and one app window at a time.
 * All state lives on the server; this screen shows it and sends commands/file saves back.
 */
public class ComputerScreen extends Screen {
	private static final Gson GSON = new Gson();

	public record Theme(String name, int accent, int accentDim, int panel, int bg, int text) {}

	public static final List<Theme> THEMES = List.of(
		new Theme("green", 0xFF4CFF7A, 0xFF1E7A3A, 0xFF16261C, 0xFF0B140E, 0xFFCFFFE0),
		new Theme("amber", 0xFFFFB13B, 0xFF8A5A12, 0xFF2A2012, 0xFF140F08, 0xFFFFE6C0),
		new Theme("ocean", 0xFF4CC3FF, 0xFF1C5E8A, 0xFF132433, 0xFF0A131C, 0xFFD6F1FF),
		new Theme("candy", 0xFFFF6FC8, 0xFF8A2C66, 0xFF2C1626, 0xFF160B13, 0xFFFFD9F0),
		new Theme("violet", 0xFFB07CFF, 0xFF5A3A99, 0xFF1F1830, 0xFF100C18, 0xFFE9DEFF));

	public final BlockPos pos;
	private String label = "";
	private Map<String, String> files = new LinkedHashMap<>();
	private List<String> terminal = new ArrayList<>();
	private String running = "";
	private int channel = -1;

	private final List<App> apps = new ArrayList<>();
	private @Nullable App open;
	private int sx, sy, sw, sh;        // screen area inside the bezel
	private int bootTicks;

	public ComputerScreen(Net.Sync sync) {
		super(Component.literal("BlockOS"));
		this.pos = sync.pos();
		this.apps.add(new TerminalApp(this));
		this.apps.add(new FilesApp(this));
		this.apps.add(new NotepadApp(this));
		this.apps.add(new PaintApp(this));
		this.apps.add(new SnakeApp(this));
		this.apps.add(new CalculatorApp(this));
		this.apps.add(new ClockApp(this));
		this.apps.add(new MessengerApp(this));
		this.apps.add(new SettingsApp(this));
		this.apply(sync);
	}

	// ------------------------------------------------------------------ data from the computer

	public void apply(Net.Sync sync) {
		this.label = sync.label();
		Map<String, String> f = GSON.fromJson(sync.filesJson(), new TypeToken<LinkedHashMap<String, String>>() {}.getType());
		this.files = f != null ? f : new LinkedHashMap<>();
		List<String> t = GSON.fromJson(sync.terminalJson(), new TypeToken<ArrayList<String>>() {}.getType());
		this.terminal = t != null ? t : new ArrayList<>();
		this.running = sync.running();
		this.channel = sync.channel();
		for (App a : this.apps) a.onSync();
	}

	public String label() {
		return this.label.isEmpty() ? "Computer" : this.label;
	}

	public Map<String, String> files() {
		return this.files;
	}

	public List<String> terminal() {
		return this.terminal;
	}

	public String running() {
		return this.running;
	}

	public int channel() {
		return this.channel;
	}

	public Font font() {
		return this.font;
	}

	public Theme theme() {
		String name = this.files.getOrDefault(".theme", "green").trim();
		for (Theme t : THEMES) if (t.name().equals(name)) return t;
		return THEMES.get(0);
	}

	public String wallpaper() {
		return this.files.getOrDefault(".wallpaper", "grid").trim();
	}

	// ------------------------------------------------------------------ talking to the computer

	public void runCommand(String line) {
		ClientPlayNetworking.send(new Net.RunCommand(this.pos, line));
	}

	public void saveFile(String path, String content) {
		this.files.put(path, content);                      // show it right away; the server confirms on next sync
		ClientPlayNetworking.send(new Net.SaveFile(this.pos, path, content, false));
	}

	public void deleteFile(String path) {
		this.files.remove(path);
		ClientPlayNetworking.send(new Net.SaveFile(this.pos, path, "", true));
	}

	// ------------------------------------------------------------------ apps

	public <T extends App> T app(Class<T> type) {
		for (App a : this.apps) if (type.isInstance(a)) return type.cast(a);
		throw new IllegalArgumentException(type.getName());
	}

	public void openApp(@Nullable App app) {
		this.open = app;
		this.layoutApp();
	}

	/** Opens an app by name (used by the Terminal and by tests). */
	public boolean openApp(String name) {
		for (App a : this.apps) {
			if (a.title().equalsIgnoreCase(name)) {
				this.openApp(a);
				return true;
			}
		}
		return false;
	}

	public void openFile(String path) {
		if (path.endsWith(".pix")) {
			this.app(PaintApp.class).load(path);
			this.openApp(this.app(PaintApp.class));
		} else {
			this.app(NotepadApp.class).load(path);
			this.openApp(this.app(NotepadApp.class));
		}
	}

	public @Nullable App openAppNow() {
		return this.open;
	}

	// ------------------------------------------------------------------ layout

	@Override
	protected void init() {
		int margin = Math.max(6, Math.min(this.width, this.height) / 30);
		this.sx = margin + 6;
		this.sy = margin + 6;
		this.sw = this.width - 2 * this.sx;
		this.sh = this.height - 2 * this.sy - 6;
		this.layoutApp();
	}

	private static final int TASKBAR = 14;
	private static final int TITLEBAR = 13;

	private void layoutApp() {
		if (this.open != null) {
			this.open.layout(this.sx + 3, this.sy + TITLEBAR + 3, this.sw - 6, this.sh - TASKBAR - TITLEBAR - 6);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void tick() {
		this.bootTicks++;
		for (App a : this.apps) a.tick();
	}

	@Override
	public void onClose() {
		ClientPlayNetworking.send(new Net.Close(this.pos));
		super.onClose();
	}

	// ------------------------------------------------------------------ drawing

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.fill(0, 0, this.width, this.height, 0xB0000000);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		Theme t = this.theme();
		// Monitor bezel
		int bx = this.sx - 8, by = this.sy - 8, bw = this.sw + 16, bh = this.sh + 22;
		g.fill(bx, by, bx + bw, by + bh, 0xFF2B2F36);
		g.fill(bx + 2, by + 2, bx + bw - 2, by + bh - 2, 0xFF3A3F48);
		g.fill(this.sx - 2, this.sy - 2, this.sx + this.sw + 2, this.sy + this.sh + 2, 0xFF111317);
		g.text(this.font, "BlockOS", bx + 8, by + bh - 11, 0xFF8A93A3, false);
		int led = this.running.isEmpty() ? 0xFF3C8C4C : ((this.bootTicks / 6) % 2 == 0 ? 0xFF4CFF7A : 0xFF2A7A3E);
		g.fill(bx + bw - 14, by + bh - 9, bx + bw - 9, by + bh - 6, led);

		g.enableScissor(this.sx, this.sy, this.sx + this.sw, this.sy + this.sh);
		this.drawWallpaper(g, t);
		if (this.bootTicks < 14) {
			this.drawBoot(g, t);
		} else if (this.open == null) {
			this.drawDesktop(g, t, mouseX, mouseY);
		} else {
			this.drawWindow(g, t, mouseX, mouseY);
		}
		this.drawTaskbar(g, t, mouseX, mouseY);
		// Faint scanlines for a CRT feel
		for (int yy = this.sy; yy < this.sy + this.sh; yy += 2) g.fill(this.sx, yy, this.sx + this.sw, yy + 1, 0x0D000000);
		g.disableScissor();
	}

	private void drawBoot(GuiGraphicsExtractor g, Theme t) {
		int cx = this.sx + this.sw / 2, cy = this.sy + this.sh / 2;
		g.centeredText(this.font, "BlockOS", cx, cy - 14, t.accent());
		int barW = 80;
		g.outline(cx - barW / 2, cy, barW, 6, t.accentDim());
		g.fill(cx - barW / 2 + 1, cy + 1, cx - barW / 2 + 1 + (barW - 2) * Math.min(14, this.bootTicks) / 14, cy + 5, t.accent());
	}

	private void drawWallpaper(GuiGraphicsExtractor g, Theme t) {
		g.fill(this.sx, this.sy, this.sx + this.sw, this.sy + this.sh, t.bg());
		switch (this.wallpaper()) {
			case "sunset" -> {
				g.fillGradient(this.sx, this.sy, this.sx + this.sw, this.sy + this.sh * 2 / 3, 0xFF2A1B4A, 0xFFFF7A59);
				g.fill(this.sx, this.sy + this.sh * 2 / 3, this.sx + this.sw, this.sy + this.sh, 0xFF1A0F24);
				int cx = this.sx + this.sw / 2, cy = this.sy + this.sh * 2 / 3;
				for (int r = 0; r < 18; r += 3) g.fill(cx - 22 + r / 2, cy - 18 + r, cx + 22 - r / 2, cy - 16 + r, 0xFFFFD27A);
			}
			case "matrix" -> {
				for (int col = 0; col < this.sw / 6; col++) {
					int seed = col * 7919;
					int head = (int) ((this.bootTicks * (1 + seed % 3) + seed) % (this.sh / 8 + 10));
					for (int k = 0; k < 8; k++) {
						int row = head - k;
						if (row < 0) continue;
						int alpha = 255 - k * 30;
						char ch = (char) ('0' + ((seed + row * 31) % 10));
						g.text(this.font, String.valueOf(ch), this.sx + col * 6, this.sy + row * 8, (alpha << 24) | (t.accent() & 0xFFFFFF), false);
					}
				}
			}
			default -> {
				g.fillGradient(this.sx, this.sy, this.sx + this.sw, this.sy + this.sh, t.bg(), t.panel());
				for (int gx = this.sx; gx < this.sx + this.sw; gx += 16) g.fill(gx, this.sy, gx + 1, this.sy + this.sh, 0x10FFFFFF);
				for (int gy = this.sy; gy < this.sy + this.sh; gy += 16) g.fill(this.sx, gy, this.sx + this.sw, gy + 1, 0x10FFFFFF);
			}
		}
	}

	// Desktop icon grid
	private static final int ICON_W = 52, ICON_H = 40;

	private int iconX(int i) {
		int cols = Math.max(1, (this.sw - 12) / ICON_W);
		return this.sx + 8 + (i % cols) * ICON_W;
	}

	private int iconY(int i) {
		int cols = Math.max(1, (this.sw - 12) / ICON_W);
		return this.sy + 8 + (i / cols) * ICON_H;
	}

	private void drawDesktop(GuiGraphicsExtractor g, Theme t, int mouseX, int mouseY) {
		for (int i = 0; i < this.apps.size(); i++) {
			App app = this.apps.get(i);
			int ix = this.iconX(i), iy = this.iconY(i);
			boolean hover = mouseX >= ix && mouseX < ix + ICON_W - 4 && mouseY >= iy && mouseY < iy + ICON_H - 4;
			if (hover) g.fill(ix, iy, ix + ICON_W - 4, iy + ICON_H - 4, 0x40FFFFFF);
			int cx = ix + (ICON_W - 4) / 2;
			g.fill(cx - 11, iy + 3, cx + 11, iy + 25, 0xFF0E1013);
			g.outline(cx - 11, iy + 3, 22, 22, hover ? t.accent() : t.accentDim());
			app.drawIcon(g, cx - 8, iy + 6);
			String name = app.title();
			g.centeredText(this.font, name, cx, iy + 27, hover ? t.accent() : t.text());
		}
		// Welcome note
		String hint = "Click an app to open it. Esc goes back / closes the computer.";
		if (this.font.width(hint) < this.sw - 16) g.text(this.font, hint, this.sx + 8, this.sy + this.sh - TASKBAR - 12, 0x80FFFFFF, false);
	}

	private void drawWindow(GuiGraphicsExtractor g, Theme t, int mouseX, int mouseY) {
		App app = this.open;
		int wx = this.sx + 1, wy = this.sy + 1, ww = this.sw - 2, wh = this.sh - TASKBAR - 2;
		g.fill(wx, wy, wx + ww, wy + wh, 0xF0101215);
		g.outline(wx, wy, ww, wh, t.accentDim());
		g.fill(wx + 1, wy + 1, wx + ww - 1, wy + TITLEBAR, t.panel());
		g.fill(wx + 1, wy + TITLEBAR, wx + ww - 1, wy + TITLEBAR + 1, t.accentDim());
		app.drawIcon(g, wx + 3, wy - 1);
		g.text(this.font, app.title() + "  -  " + this.label(), wx + 22, wy + 3, t.text(), false);
		// close button
		int cx = wx + ww - 13;
		boolean hoverX = mouseX >= cx && mouseX < cx + 11 && mouseY >= wy + 1 && mouseY < wy + 12;
		g.fill(cx, wy + 1, cx + 11, wy + 12, hoverX ? 0xFFE0484E : 0xFF5A2A2E);
		g.text(this.font, "x", cx + 3, wy + 2, 0xFFFFFFFF, false);
		g.enableScissor(wx + 1, wy + TITLEBAR + 1, wx + ww - 1, wy + wh - 1);
		app.render(g, mouseX, mouseY);
		g.disableScissor();
	}

	private void drawTaskbar(GuiGraphicsExtractor g, Theme t, int mouseX, int mouseY) {
		int ty = this.sy + this.sh - TASKBAR;
		g.fill(this.sx, ty, this.sx + this.sw, this.sy + this.sh, 0xE0090A0C);
		g.fill(this.sx, ty, this.sx + this.sw, ty + 1, t.accentDim());
		// "home" button
		boolean hoverHome = mouseX >= this.sx + 2 && mouseX < this.sx + 50 && mouseY >= ty;
		g.fill(this.sx + 2, ty + 2, this.sx + 50, this.sy + this.sh - 2, hoverHome ? t.accent() : t.panel());
		g.text(this.font, "BlockOS", this.sx + 7, ty + 3, hoverHome ? 0xFF101010 : t.accent(), false);
		String status = this.running.isEmpty() ? this.label() : this.label() + "  -  running " + this.running;
		if (this.channel >= 0) status += "  -  ch " + this.channel;
		g.text(this.font, status, this.sx + 56, ty + 3, t.text(), false);
		String clock = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
		g.text(this.font, clock, this.sx + this.sw - this.font.width(clock) - 5, ty + 3, t.accent(), false);
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		double mx = e.x(), my = e.y();
		int ty = this.sy + this.sh - TASKBAR;
		if (my >= ty && my < this.sy + this.sh && mx >= this.sx + 2 && mx < this.sx + 50) {
			this.openApp((App) null);
			return true;
		}
		if (this.open == null) {
			for (int i = 0; i < this.apps.size(); i++) {
				int ix = this.iconX(i), iy = this.iconY(i);
				if (mx >= ix && mx < ix + ICON_W - 4 && my >= iy && my < iy + ICON_H - 4) {
					this.openApp(this.apps.get(i));
					return true;
				}
			}
			return true;
		}
		int wx = this.sx + 1, wy = this.sy + 1, ww = this.sw - 2;
		int cx = wx + ww - 13;
		if (mx >= cx && mx < cx + 11 && my >= wy + 1 && my < wy + 12) {
			this.openApp((App) null);
			return true;
		}
		return this.open.mouseClicked(mx, my, e.button(), doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
		return this.open != null && this.open.mouseDragged(e.x(), e.y(), e.button());
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent e) {
		if (this.open != null) this.open.mouseReleased();
		return true;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double sx, double sy) {
		return this.open != null && this.open.mouseScrolled(sy);
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (e.key() == InputConstants.KEY_ESCAPE) {
			if (this.open != null) this.openApp((App) null);
			else this.onClose();
			return true;
		}
		if (this.open != null && this.open.keyPressed(e)) return true;
		return true;   // swallow everything else so typing never moves the player or opens inventory
	}

	@Override
	public boolean charTyped(CharacterEvent e) {
		return this.open != null && this.open.charTyped(e);
	}
}
