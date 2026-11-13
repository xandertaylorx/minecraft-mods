package com.blockos.client.apps;

import com.blockos.client.screen.ComputerScreen;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayDeque;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;

/** Classic snake. Arrows/WASD to steer, Space to start. High score is saved on the computer. */
public class SnakeApp extends App {
	private static final int GW = 28, GH = 16;
	private final ArrayDeque<int[]> body = new ArrayDeque<>();
	private final Random random = new Random();
	private int dx = 1, dy, nextDx = 1, nextDy;
	private int foodX, foodY;
	private int score;
	private boolean alive;
	private boolean started;
	private int timer;

	public SnakeApp(ComputerScreen os) {
		super(os);
		this.reset();
	}

	@Override
	public String title() {
		return "Snake";
	}

	@Override
	public void drawIcon(GuiGraphicsExtractor g, int ix, int iy) {
		g.fill(ix + 2, iy + 10, ix + 12, iy + 12, 0xFF5BD25B);
		g.fill(ix + 10, iy + 4, ix + 12, iy + 12, 0xFF5BD25B);
		g.fill(ix + 10, iy + 4, ix + 14, iy + 6, 0xFF7CFF7C);
		g.fill(ix + 3, iy + 4, ix + 5, iy + 6, 0xFFE0302F);
	}

	private void reset() {
		this.body.clear();
		for (int i = 0; i < 4; i++) this.body.addFirst(new int[] {4 + i, GH / 2});
		this.dx = this.nextDx = 1;
		this.dy = this.nextDy = 0;
		this.score = 0;
		this.alive = true;
		this.placeFood();
	}

	private void placeFood() {
		do {
			this.foodX = this.random.nextInt(GW);
			this.foodY = this.random.nextInt(GH);
		} while (this.occupied(this.foodX, this.foodY));
	}

	private boolean occupied(int x, int y) {
		for (int[] p : this.body) if (p[0] == x && p[1] == y) return true;
		return false;
	}

	private int best() {
		try {
			return Integer.parseInt(this.os.files().getOrDefault(".snake_best", "0").trim());
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	@Override
	public void tick() {
		if (this.os.openAppNow() != this || !this.started || !this.alive) return;
		int speed = Math.max(2, 5 - this.score / 5);
		if (++this.timer < speed) return;
		this.timer = 0;
		this.dx = this.nextDx;
		this.dy = this.nextDy;
		int[] head = this.body.peekFirst();
		int nx = head[0] + this.dx, ny = head[1] + this.dy;
		if (nx < 0 || ny < 0 || nx >= GW || ny >= GH || this.occupied(nx, ny)) {
			this.alive = false;
			if (this.score > this.best()) this.os.saveFile(".snake_best", String.valueOf(this.score));
			return;
		}
		this.body.addFirst(new int[] {nx, ny});
		if (nx == this.foodX && ny == this.foodY) {
			this.score++;
			this.placeFood();
		} else {
			this.body.removeLast();
		}
	}

	@Override
	public void render(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		ComputerScreen.Theme t = this.os.theme();
		int c = Math.max(3, Math.min((this.w - 8) / GW, (this.h - 18) / GH));
		int ox = this.x + (this.w - GW * c) / 2, oy = this.y + 14;
		g.text(this.font(), "Score " + this.score + "   Best " + Math.max(this.best(), this.score), this.x + 4, this.y + 3, t.text(), false);
		g.fill(ox, oy, ox + GW * c, oy + GH * c, 0xFF0B1A10);
		g.outline(ox - 1, oy - 1, GW * c + 2, GH * c + 2, t.accentDim());
		g.fill(ox + this.foodX * c + 1, oy + this.foodY * c + 1, ox + this.foodX * c + c - 1, oy + this.foodY * c + c - 1, 0xFFE0302F);
		boolean first = true;
		for (int[] p : this.body) {
			g.fill(ox + p[0] * c, oy + p[1] * c, ox + p[0] * c + c - 1, oy + p[1] * c + c - 1, first ? 0xFF9CFF9C : 0xFF4FC74F);
			first = false;
		}
		String msg = !this.started ? "Press SPACE to play  (arrows / WASD)" : !this.alive ? "Game over! SPACE to try again" : null;
		if (msg != null) {
			int mw = this.font().width(msg) + 10;
			int mx = this.x + (this.w - mw) / 2, my = oy + GH * c / 2 - 7;
			g.fill(mx, my, mx + mw, my + 14, 0xE0000000);
			g.text(this.font(), msg, mx + 5, my + 3, t.accent(), false);
		}
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		int k = e.key();
		if (k == InputConstants.KEY_SPACE) {
			if (!this.started || !this.alive) {
				this.reset();
				this.started = true;
			}
			return true;
		}
		if ((k == InputConstants.KEY_UP || k == InputConstants.KEY_W) && this.dy == 0) {
			this.nextDx = 0;
			this.nextDy = -1;
		} else if ((k == InputConstants.KEY_DOWN || k == InputConstants.KEY_S) && this.dy == 0) {
			this.nextDx = 0;
			this.nextDy = 1;
		} else if ((k == InputConstants.KEY_LEFT || k == InputConstants.KEY_A) && this.dx == 0) {
			this.nextDx = -1;
			this.nextDy = 0;
		} else if ((k == InputConstants.KEY_RIGHT || k == InputConstants.KEY_D) && this.dx == 0) {
			this.nextDx = 1;
			this.nextDy = 0;
		} else {
			return false;
		}
		return true;
	}
}
