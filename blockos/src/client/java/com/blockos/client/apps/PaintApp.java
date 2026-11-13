package com.blockos.client.apps;

import com.blockos.client.screen.ComputerScreen;
import java.util.ArrayDeque;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Pixel art. Pictures are saved as .pix files: "W H" then one hex digit (palette index) per pixel. */
public class PaintApp extends App {
	public static final int[] PALETTE = {
		0xFF000000, 0xFFFFFFFF, 0xFF7F7F7F, 0xFFC3C3C3, 0xFFE0302F, 0xFFFF8A3D, 0xFFFFD84A, 0xFF5BD25B,
		0xFF2E7D32, 0xFF4FC3F7, 0xFF1E5BD6, 0xFF8E4CE0, 0xFFFF7AC8, 0xFF8A5A2E, 0xFF3A2A1E, 0x00000000
	};
	private static final int CW = 48, CH = 30;

	private final int[] pixels = new int[CW * CH];
	private int color = 0;
	private int tool;            // 0 pencil, 1 bucket, 2 eraser
	private String path = "pictures/drawing.pix";
	private String status = "";
	private int statusTicks;

	public PaintApp(ComputerScreen os) {
		super(os);
		this.clear();
	}

	private void clear() {
		java.util.Arrays.fill(this.pixels, 1);
	}

	@Override
	public String title() {
		return "Paint";
	}

	@Override
	public void drawIcon(GuiGraphicsExtractor g, int ix, int iy) {
		g.fill(ix + 1, iy + 2, ix + 15, iy + 14, 0xFFF4F1E6);
		g.fill(ix + 3, iy + 4, ix + 7, iy + 8, 0xFFE0302F);
		g.fill(ix + 8, iy + 5, ix + 12, iy + 9, 0xFF1E5BD6);
		g.fill(ix + 5, iy + 9, ix + 9, iy + 13, 0xFFFFD84A);
		g.fill(ix + 11, iy + 9, ix + 13, iy + 13, 0xFF5BD25B);
	}

	public void load(String path) {
		this.path = path;
		this.clear();
		String data = this.os.files().get(path);
		if (data == null) return;
		String[] parts = data.split("\n", 2);
		if (parts.length < 2) return;
		String body = parts[1].replace("\n", "");
		for (int i = 0; i < Math.min(body.length(), this.pixels.length); i++) {
			this.pixels[i] = Character.digit(body.charAt(i), 16) & 15;
		}
	}

	private void save() {
		StringBuilder sb = new StringBuilder(CW + " " + CH + "\n");
		for (int yy = 0; yy < CH; yy++) {
			for (int xx = 0; xx < CW; xx++) sb.append(Integer.toHexString(this.pixels[yy * CW + xx]));
			sb.append('\n');
		}
		this.os.saveFile(this.path, sb.toString());
		this.status = "Saved " + this.path;
		this.statusTicks = 60;
	}

	@Override
	public void tick() {
		if (this.statusTicks > 0) this.statusTicks--;
	}

	private int cell() {
		return Math.max(2, Math.min((this.w - 60) / CW, (this.h - 22) / CH));
	}

	private int canvasX() {
		return this.x + 4;
	}

	private int canvasY() {
		return this.y + 18;
	}

	@Override
	public void render(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		ComputerScreen.Theme t = this.os.theme();
		String[] tools = {"Pen", "Fill", "Erase"};
		for (int i = 0; i < 3; i++) {
			int bx = this.x + 4 + i * 34;
			boolean sel = i == this.tool;
			g.fill(bx, this.y + 2, bx + 32, this.y + 14, sel ? t.accent() : t.panel());
			g.outline(bx, this.y + 2, 32, 12, t.accentDim());
			g.centeredText(this.font(), tools[i], bx + 16, this.y + 4, sel ? 0xFF101010 : t.text());
		}
		g.text(this.font(), this.statusTicks > 0 ? this.status : this.path, this.x + 110, this.y + 4, this.statusTicks > 0 ? t.accent() : t.text(), false);
		this.button(g, this.x + this.w - 140, this.y + 2, 32, 12, "Save", mouseX, mouseY);
		this.button(g, this.x + this.w - 104, this.y + 2, 32, 12, "Clear", mouseX, mouseY);
		this.button(g, this.x + this.w - 68, this.y + 2, 64, 12, "New name", mouseX, mouseY);

		int c = this.cell();
		int cx = this.canvasX(), cy = this.canvasY();
		// checkerboard behind transparent pixels
		for (int yy = 0; yy < CH; yy++) {
			for (int xx = 0; xx < CW; xx++) {
				int p = this.pixels[yy * CW + xx];
				int col = p == 15 ? (((xx + yy) & 1) == 0 ? 0xFF3A3A3A : 0xFF2A2A2A) : PALETTE[p];
				g.fill(cx + xx * c, cy + yy * c, cx + xx * c + c, cy + yy * c + c, col);
			}
		}
		g.outline(cx - 1, cy - 1, CW * c + 2, CH * c + 2, t.accentDim());

		// palette + tools on the right
		int px = cx + CW * c + 8;
		for (int i = 0; i < 16; i++) {
			int bx = px + (i % 2) * 14, by = cy + (i / 2) * 14;
			g.fill(bx, by, bx + 12, by + 12, i == 15 ? 0xFF444444 : PALETTE[i]);
			if (i == 15) g.text(this.font(), "x", bx + 3, by + 2, 0xFFFFFFFF, false);
			if (i == this.color) g.outline(bx - 1, by - 1, 14, 14, t.accent());
		}
	}

	/** Screen position of a canvas cell's center (used by tests). */
	public double[] cellCenter(int gx, int gy) {
		int c = this.cell();
		return new double[] {this.canvasX() + gx * c + c / 2.0, this.canvasY() + gy * c + c / 2.0};
	}

	public void selectColor(int i) {
		this.color = i;
		this.tool = 0;
	}

	private void paintAt(double mx, double my) {
		int c = this.cell();
		int gx = (int) ((mx - this.canvasX()) / c), gy = (int) ((my - this.canvasY()) / c);
		if (gx < 0 || gy < 0 || gx >= CW || gy >= CH) return;
		int target = this.tool == 2 ? 1 : this.color;
		if (this.tool == 1) this.fill(gx, gy, target);
		else this.pixels[gy * CW + gx] = target;
	}

	private void fill(int sx, int sy, int to) {
		int from = this.pixels[sy * CW + sx];
		if (from == to) return;
		ArrayDeque<int[]> q = new ArrayDeque<>();
		q.add(new int[] {sx, sy});
		while (!q.isEmpty()) {
			int[] p = q.poll();
			if (p[0] < 0 || p[1] < 0 || p[0] >= CW || p[1] >= CH || this.pixels[p[1] * CW + p[0]] != from) continue;
			this.pixels[p[1] * CW + p[0]] = to;
			q.add(new int[] {p[0] + 1, p[1]});
			q.add(new int[] {p[0] - 1, p[1]});
			q.add(new int[] {p[0], p[1] + 1});
			q.add(new int[] {p[0], p[1] - 1});
		}
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button, boolean doubleClick) {
		if (this.inside(mx, my, this.x + this.w - 140, this.y + 2, 32, 12)) {
			this.save();
			return true;
		}
		if (this.inside(mx, my, this.x + this.w - 104, this.y + 2, 32, 12)) {
			this.clear();
			return true;
		}
		if (this.inside(mx, my, this.x + this.w - 68, this.y + 2, 64, 12)) {
			int n = 1;
			while (this.os.files().containsKey("pictures/drawing" + n + ".pix")) n++;
			this.path = "pictures/drawing" + n + ".pix";
			this.status = "New picture: " + this.path;
			this.statusTicks = 60;
			return true;
		}
		int c = this.cell();
		int px = this.canvasX() + CW * c + 8, cy = this.canvasY();
		for (int i = 0; i < 16; i++) {
			if (this.inside(mx, my, px + (i % 2) * 14, cy + (i / 2) * 14, 12, 12)) {
				this.color = i;
				if (this.tool == 2) this.tool = 0;
				return true;
			}
		}
		for (int i = 0; i < 3; i++) {
			if (this.inside(mx, my, this.x + 4 + i * 34, this.y + 2, 32, 12)) {
				this.tool = i;
				return true;
			}
		}
		this.paintAt(mx, my);
		return true;
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button) {
		if (this.tool != 1) this.paintAt(mx, my);
		return true;
	}
}
