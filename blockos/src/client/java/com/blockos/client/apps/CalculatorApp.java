package com.blockos.client.apps;

import com.blockos.client.screen.ComputerScreen;
import com.blockos.shell.Expr;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.HashMap;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

public class CalculatorApp extends App {
	private static final String[][] KEYS = {
		{"C", "(", ")", "/"},
		{"7", "8", "9", "*"},
		{"4", "5", "6", "-"},
		{"1", "2", "3", "+"},
		{"0", ".", "^", "="},
	};
	private String expr = "";
	private String result = "0";

	public CalculatorApp(ComputerScreen os) {
		super(os);
	}

	@Override
	public String title() {
		return "Calc";
	}

	@Override
	public void drawIcon(GuiGraphicsExtractor g, int ix, int iy) {
		g.fill(ix + 3, iy + 1, ix + 13, iy + 15, 0xFF3A3F48);
		g.fill(ix + 4, iy + 2, ix + 12, iy + 5, 0xFF9CFFB0);
		for (int r = 0; r < 3; r++) for (int c = 0; c < 3; c++) g.fill(ix + 4 + c * 3, iy + 7 + r * 3, ix + 6 + c * 3, iy + 9 + r * 3, 0xFFDDE3EA);
	}

	private int keyW() {
		return Math.min(36, (this.w - 16) / 4);
	}

	@Override
	public void render(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		ComputerScreen.Theme t = this.os.theme();
		int kw = this.keyW(), kh = 16;
		int pw = kw * 4 + 12;
		int px = this.x + (this.w - pw) / 2, py = this.y + 4;
		g.fill(px, py, px + pw, py + 30, 0xFF0B1A10);
		g.outline(px, py, pw, 30, t.accentDim());
		g.text(this.font(), this.expr.isEmpty() ? " " : this.expr, px + 4, py + 4, 0x90FFFFFF, false);
		g.text(this.font(), this.result, px + pw - 4 - this.font().width(this.result), py + 18, t.accent(), false);
		for (int r = 0; r < KEYS.length; r++) {
			for (int c = 0; c < 4; c++) {
				int bx = px + 2 + c * (kw + 2), by = py + 34 + r * (kh + 2);
				this.button(g, bx, by, kw, kh, KEYS[r][c], mouseX, mouseY);
			}
		}
		g.text(this.font(), "Type maths too. Try: sqrt(2) * 10", px, py + 34 + 5 * (kh + 2) + 2, 0x70FFFFFF, false);
	}

	private void press(String k) {
		switch (k) {
			case "C" -> {
				this.expr = "";
				this.result = "0";
			}
			case "=" -> {
				try {
					this.result = Expr.str(Expr.eval(this.expr, new HashMap<>(), (name, args) -> switch (name) {
						case "sqrt" -> Math.sqrt(Expr.num(args.get(0)));
						case "abs" -> Math.abs(Expr.num(args.get(0)));
						case "round" -> (double) Math.round(Expr.num(args.get(0)));
						case "sin" -> Math.sin(Math.toRadians(Expr.num(args.get(0))));
						case "cos" -> Math.cos(Math.toRadians(Expr.num(args.get(0))));
						default -> throw new Expr.ExprError("Unknown " + name);
					}));
				} catch (RuntimeException e) {
					this.result = "Error";
				}
			}
			default -> {
				if (this.expr.length() < 40) this.expr += k;
			}
		}
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button, boolean doubleClick) {
		int kw = this.keyW(), kh = 16;
		int pw = kw * 4 + 12;
		int px = this.x + (this.w - pw) / 2, py = this.y + 4;
		for (int r = 0; r < KEYS.length; r++) {
			for (int c = 0; c < 4; c++) {
				if (this.inside(mx, my, px + 2 + c * (kw + 2), py + 34 + r * (kh + 2), kw, kh)) this.press(KEYS[r][c]);
			}
		}
		return true;
	}

	@Override
	public boolean charTyped(CharacterEvent e) {
		String s = e.codepointAsString();
		if ("0123456789.+-*/()^%abcdefghijklmnopqrstuvwxyz".contains(s.toLowerCase()) && this.expr.length() < 40) this.expr += s;
		if (s.equals("=")) this.press("=");
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (e.key() == InputConstants.KEY_RETURN) this.press("=");
		else if (e.key() == InputConstants.KEY_BACKSPACE && !this.expr.isEmpty()) this.expr = this.expr.substring(0, this.expr.length() - 1);
		else if (e.key() == InputConstants.KEY_DELETE) this.press("C");
		else return false;
		return true;
	}
}
