package com.blockos.client.apps;

import com.blockos.client.screen.ComputerScreen;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

/** Multi-line text editor. Ctrl/Cmd+S saves. BlockScript files get colors and a Run button. */
public class NotepadApp extends App {
	private static final Set<String> KEYWORDS = Set.of("if", "else", "end", "while", "loop", "repeat", "set", "wait", "break", "stop", "print");
	private static final Set<String> COMMANDS = Set.of("redstone", "input", "send", "listen", "say", "play", "beep", "run", "write", "append", "echo", "scan", "label");

	private final List<StringBuilder> lines = new ArrayList<>();
	private String path = "notes/untitled.txt";
	private int row, col, scroll;
	private boolean dirty;
	private boolean editingName;
	private final StringBuilder nameBuf = new StringBuilder();
	private int blink;
	private String status = "";
	private int statusTicks;

	public NotepadApp(ComputerScreen os) {
		super(os);
		this.lines.add(new StringBuilder());
	}

	@Override
	public String title() {
		return "Notepad";
	}

	@Override
	public void drawIcon(GuiGraphicsExtractor g, int ix, int iy) {
		g.fill(ix + 2, iy + 1, ix + 14, iy + 15, 0xFFF4F1E6);
		g.fill(ix + 2, iy + 1, ix + 14, iy + 3, 0xFF5AA0FF);
		for (int i = 0; i < 4; i++) g.fill(ix + 4, iy + 5 + i * 2, ix + 12 - (i % 2) * 3, iy + 6 + i * 2, 0xFF7A7F8A);
	}

	public void newFile() {
		this.load("notes/untitled.txt");
		this.lines.clear();
		this.lines.add(new StringBuilder());
	}

	public void load(String path) {
		this.path = path;
		this.lines.clear();
		String content = this.os.files().getOrDefault(path, "");
		for (String l : content.split("\n", -1)) this.lines.add(new StringBuilder(l));
		if (this.lines.isEmpty()) this.lines.add(new StringBuilder());
		this.row = 0;
		this.col = 0;
		this.scroll = 0;
		this.dirty = false;
		this.editingName = false;
	}

	private String text() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < this.lines.size(); i++) {
			if (i > 0) sb.append('\n');
			sb.append(this.lines.get(i));
		}
		return sb.toString();
	}

	private void save() {
		this.os.saveFile(this.path, this.text());
		this.dirty = false;
		this.flash("Saved " + this.path);
	}

	private void flash(String s) {
		this.status = s;
		this.statusTicks = 60;
	}

	@Override
	public void tick() {
		this.blink++;
		if (this.statusTicks > 0) this.statusTicks--;
	}

	@Override
	public void render(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		ComputerScreen.Theme t = this.os.theme();
		boolean script = this.path.endsWith(".bs");
		// toolbar
		g.fill(this.x, this.y, this.x + this.w, this.y + 15, 0xFF14171B);
		String name = this.editingName ? this.nameBuf + ((this.blink / 8) % 2 == 0 ? "_" : " ") : this.path + (this.dirty ? " *" : "");
		g.fill(this.x + 3, this.y + 2, this.x + 150, this.y + 13, this.editingName ? 0xFF22282F : 0xFF101317);
		g.outline(this.x + 3, this.y + 2, 147, 11, this.editingName ? t.accent() : 0xFF2C333B);
		g.text(this.font(), name, this.x + 6, this.y + 4, this.editingName ? t.accent() : t.text(), false);
		this.button(g, this.x + 154, this.y + 2, 34, 11, "Save", mouseX, mouseY);
		this.button(g, this.x + 192, this.y + 2, 34, 11, "New", mouseX, mouseY);
		if (script) this.button(g, this.x + 230, this.y + 2, 34, 11, "Run", mouseX, mouseY);
		if (this.statusTicks > 0) g.text(this.font(), this.status, this.x + this.w - this.font().width(this.status) - 5, this.y + 4, t.accent(), false);

		// text area with line numbers
		int top = this.y + 18;
		int lineH = 10;
		int visible = (this.h - 20) / lineH;
		if (this.row < this.scroll) this.scroll = this.row;
		if (this.row >= this.scroll + visible) this.scroll = this.row - visible + 1;
		int gutter = 22;
		g.fill(this.x, top - 1, this.x + gutter, this.y + this.h, 0xFF0D0F12);
		for (int i = 0; i < visible && i + this.scroll < this.lines.size(); i++) {
			int li = i + this.scroll;
			int ly = top + i * lineH;
			if (li == this.row) g.fill(this.x + gutter, ly - 1, this.x + this.w, ly + lineH - 1, 0x14FFFFFF);
			String num = String.valueOf(li + 1);
			g.text(this.font(), num, this.x + gutter - 3 - this.font().width(num), ly, 0x60FFFFFF, false);
			String s = this.lines.get(li).toString();
			if (script) this.drawScriptLine(g, s, this.x + gutter + 4, ly, t);
			else g.text(this.font(), s, this.x + gutter + 4, ly, t.text(), false);
			if (li == this.row && !this.editingName && (this.blink / 8) % 2 == 0) {
				int cx = this.x + gutter + 4 + this.font().width(s.substring(0, Math.min(this.col, s.length())));
				g.fill(cx, ly - 1, cx + 1, ly + 9, t.accent());
			}
		}
	}

	/** Simple syntax colors: comments, keywords, commands, strings, numbers. */
	private void drawScriptLine(GuiGraphicsExtractor g, String s, int lx, int ly, ComputerScreen.Theme t) {
		int hash = s.indexOf('#');
		String code = hash >= 0 ? s.substring(0, hash) : s;
		int xx = lx;
		for (String tok : code.split("(?<=\\s)|(?=\\s)|(?=[(),])|(?<=[(),])")) {
			String bare = tok.trim().toLowerCase();
			int col = KEYWORDS.contains(bare) ? 0xFFFF79C6
				: COMMANDS.contains(bare) ? 0xFF8BE9FD
				: tok.startsWith("\"") ? 0xFFF1FA8C
				: bare.matches("-?[0-9.]+") ? 0xFFBD93F9
				: tok.startsWith("$") ? 0xFFFFB86C : t.text();
			g.text(this.font(), tok, xx, ly, col, false);
			xx += this.font().width(tok);
		}
		if (hash >= 0) g.text(this.font(), s.substring(hash), xx, ly, 0xFF6A7A8A, false);
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button, boolean doubleClick) {
		if (this.inside(mx, my, this.x + 3, this.y + 2, 147, 11)) {
			this.editingName = true;
			this.nameBuf.setLength(0);
			this.nameBuf.append(this.path);
			return true;
		}
		this.editingName = false;
		if (this.inside(mx, my, this.x + 154, this.y + 2, 34, 11)) this.save();
		else if (this.inside(mx, my, this.x + 192, this.y + 2, 34, 11)) this.newFile();
		else if (this.path.endsWith(".bs") && this.inside(mx, my, this.x + 230, this.y + 2, 34, 11)) {
			this.save();
			this.os.runCommand("stop");
			this.os.runCommand("run " + this.path);
			this.os.openApp("Terminal");
		} else if (my >= this.y + 18) {
			int li = (int) ((my - this.y - 18) / 10) + this.scroll;
			this.row = Math.max(0, Math.min(this.lines.size() - 1, li));
			String s = this.lines.get(this.row).toString();
			int c = 0;
			while (c < s.length() && this.x + 26 + this.font().width(s.substring(0, c + 1)) < mx) c++;
			this.col = c;
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double amount) {
		this.row = Math.max(0, Math.min(this.lines.size() - 1, this.row - (int) Math.signum(amount) * 3));
		return true;
	}

	@Override
	public boolean charTyped(CharacterEvent e) {
		if (!e.isAllowedChatCharacter()) return true;
		if (this.editingName) {
			if (this.nameBuf.length() < 60) this.nameBuf.append(e.codepointAsString());
			return true;
		}
		StringBuilder line = this.lines.get(this.row);
		this.col = Math.min(this.col, line.length());
		line.insert(this.col, e.codepointAsString());
		this.col += e.codepointAsString().length();
		this.dirty = true;
		this.blink = 0;
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (e.hasControlDownWithQuirk() && e.key() == InputConstants.KEY_S) {
			this.save();
			return true;
		}
		if (this.editingName) {
			if (e.key() == InputConstants.KEY_BACKSPACE && this.nameBuf.length() > 0) this.nameBuf.setLength(this.nameBuf.length() - 1);
			if (e.key() == InputConstants.KEY_RETURN) {
				String n = this.nameBuf.toString().trim();
				if (!n.isEmpty()) {
					this.path = n;
					this.save();
				}
				this.editingName = false;
			}
			return true;
		}
		StringBuilder line = this.lines.get(this.row);
		this.col = Math.min(this.col, line.length());
		switch (e.key()) {
			case InputConstants.KEY_RETURN -> {
				String rest = line.substring(this.col);
				line.setLength(this.col);
				String indent = line.toString().replaceAll("^(\\s*).*$", "$1");   // keep indentation
				this.lines.add(this.row + 1, new StringBuilder(indent + rest));
				this.row++;
				this.col = indent.length();
				this.dirty = true;
			}
			case InputConstants.KEY_BACKSPACE -> {
				if (this.col > 0) {
					line.deleteCharAt(this.col - 1);
					this.col--;
				} else if (this.row > 0) {
					StringBuilder prev = this.lines.get(this.row - 1);
					this.col = prev.length();
					prev.append(line);
					this.lines.remove(this.row);
					this.row--;
				}
				this.dirty = true;
			}
			case InputConstants.KEY_DELETE -> {
				if (this.col < line.length()) line.deleteCharAt(this.col);
				else if (this.row < this.lines.size() - 1) {
					line.append(this.lines.get(this.row + 1));
					this.lines.remove(this.row + 1);
				}
				this.dirty = true;
			}
			case InputConstants.KEY_TAB -> {
				line.insert(this.col, "  ");
				this.col += 2;
				this.dirty = true;
			}
			case InputConstants.KEY_LEFT -> {
				if (this.col > 0) this.col--;
				else if (this.row > 0) {
					this.row--;
					this.col = this.lines.get(this.row).length();
				}
			}
			case InputConstants.KEY_RIGHT -> {
				if (this.col < line.length()) this.col++;
				else if (this.row < this.lines.size() - 1) {
					this.row++;
					this.col = 0;
				}
			}
			case InputConstants.KEY_UP -> this.row = Math.max(0, this.row - 1);
			case InputConstants.KEY_DOWN -> this.row = Math.min(this.lines.size() - 1, this.row + 1);
			case InputConstants.KEY_HOME -> this.col = 0;
			case InputConstants.KEY_END -> this.col = line.length();
			default -> {
				return false;
			}
		}
		this.blink = 0;
		return true;
	}
}
