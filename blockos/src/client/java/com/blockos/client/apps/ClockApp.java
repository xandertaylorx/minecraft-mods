package com.blockos.client.apps;

import com.blockos.client.screen.ComputerScreen;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Real-world clock in big pixel digits, the date, Minecraft time, and a stopwatch. */
public class ClockApp extends App {
	// 3x5 pixel font for digits and ':'
	private static final String[] DIGITS = {
		"111101101101111", "010110010010111", "111001111100111", "111001111001111", "101101111001001",
		"111100111001111", "111100111101111", "111001010010010", "111101111101111", "111101111001111"};

	private boolean running;
	private long startMillis;
	private long elapsed;

	public ClockApp(ComputerScreen os) {
		super(os);
	}

	@Override
	public String title() {
		return "Clock";
	}

	@Override
	public void drawIcon(GuiGraphicsExtractor g, int ix, int iy) {
		g.fill(ix + 2, iy + 2, ix + 14, iy + 14, 0xFFDDE3EA);
		g.fill(ix + 3, iy + 3, ix + 13, iy + 13, 0xFF1B1F24);
		g.fill(ix + 7, iy + 4, ix + 9, iy + 9, 0xFFFFFFFF);
		g.fill(ix + 8, iy + 8, ix + 12, iy + 10, this.os.theme().accent());
	}

	private void bigText(GuiGraphicsExtractor g, String s, int sx, int sy, int px, int color) {
		int xx = sx;
		for (char ch : s.toCharArray()) {
			if (ch == ':') {
				g.fill(xx + px, sy + px, xx + 2 * px, sy + 2 * px, color);
				g.fill(xx + px, sy + 3 * px, xx + 2 * px, sy + 4 * px, color);
				xx += 3 * px;
				continue;
			}
			String bits = DIGITS[ch - '0'];
			for (int i = 0; i < 15; i++) {
				if (bits.charAt(i) == '1') g.fill(xx + (i % 3) * px, sy + (i / 3) * px, xx + (i % 3) * px + px, sy + (i / 3) * px + px, color);
			}
			xx += 4 * px;
		}
	}

	private static int bigWidth(String s, int px) {
		int w = 0;
		for (char ch : s.toCharArray()) w += (ch == ':' ? 3 : 4) * px;
		return w - px;
	}

	@Override
	public void render(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		ComputerScreen.Theme t = this.os.theme();
		LocalDateTime now = LocalDateTime.now();
		String time = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
		int px = Math.max(2, Math.min(6, (this.w - 20) / 31));
		int tw = bigWidth(time, px);
		int cx = this.x + (this.w - tw) / 2, cy = this.y + 10;
		this.bigText(g, time, cx + 1, cy + 1, px, 0x60000000);
		this.bigText(g, time, cx, cy, px, t.accent());
		String date = now.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy"));
		g.centeredText(this.font(), date, this.x + this.w / 2, cy + 5 * px + 8, t.text());

		Minecraft mc = Minecraft.getInstance();
		if (mc.level != null) {
			long ticks = mc.level.getOverworldClockTime();
			long dt = ticks % 24000L;
			int hours = (int) ((dt / 1000 + 6) % 24), mins = (int) (dt % 1000 * 60 / 1000);
			String mcTime = String.format("Minecraft: day %d, %02d:%02d", ticks / 24000L + 1, hours, mins);
			g.centeredText(this.font(), mcTime, this.x + this.w / 2, cy + 5 * px + 20, 0xA0FFFFFF);
		}

		// Stopwatch
		long ms = this.elapsed + (this.running ? System.currentTimeMillis() - this.startMillis : 0);
		String sw = String.format("%02d:%02d.%d", ms / 60000, ms / 1000 % 60, ms / 100 % 10);
		int sy = this.y + this.h - 34;
		g.centeredText(this.font(), "Stopwatch  " + sw, this.x + this.w / 2, sy, t.text());
		this.button(g, this.x + this.w / 2 - 62, sy + 12, 60, 13, this.running ? "Pause" : "Start", mouseX, mouseY);
		this.button(g, this.x + this.w / 2 + 2, sy + 12, 60, 13, "Reset", mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button, boolean doubleClick) {
		int sy = this.y + this.h - 34;
		if (this.inside(mx, my, this.x + this.w / 2 - 62, sy + 12, 60, 13)) {
			if (this.running) this.elapsed += System.currentTimeMillis() - this.startMillis;
			else this.startMillis = System.currentTimeMillis();
			this.running = !this.running;
		} else if (this.inside(mx, my, this.x + this.w / 2 + 2, sy + 12, 60, 13)) {
			this.running = false;
			this.elapsed = 0;
		}
		return true;
	}
}
