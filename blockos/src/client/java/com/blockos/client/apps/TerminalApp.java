package com.blockos.client.apps;

import com.blockos.client.screen.ComputerScreen;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

public class TerminalApp extends App {
	private final StringBuilder input = new StringBuilder();
	private final List<String> history = new ArrayList<>();
	private int historyPos = -1;
	private int scroll;          // lines scrolled up from the bottom
	private int blink;

	public TerminalApp(ComputerScreen os) {
		super(os);
	}

	@Override
	public String title() {
		return "Terminal";
	}

	@Override
	public void drawIcon(GuiGraphicsExtractor g, int ix, int iy) {
		g.fill(ix, iy, ix + 16, iy + 14, 0xFF050607);
		g.outline(ix, iy, 16, 14, 0xFF6B7380);
		int a = this.os.theme().accent();
		g.fill(ix + 3, iy + 4, ix + 5, iy + 6, a);
		g.fill(ix + 5, iy + 6, ix + 7, iy + 8, a);
		g.fill(ix + 3, iy + 8, ix + 5, iy + 10, a);
		g.fill(ix + 8, iy + 9, ix + 13, iy + 10, a);
	}

	@Override
	public void tick() {
		this.blink++;
	}

	@Override
	public void onSync() {
		this.scroll = 0;
	}

	@Override
	public void render(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		ComputerScreen.Theme t = this.os.theme();
		g.fill(this.x, this.y, this.x + this.w, this.y + this.h, 0xFF07090B);
		int lineH = 9;
		int rows = (this.h - 4) / lineH - 1;
		List<String> wrapped = new ArrayList<>();
		for (String line : this.os.terminal()) wrapped.addAll(this.wrap(line, this.w - 8));
		String prompt = this.os.label().replace(' ', '_') + "> ";
		String inputLine = prompt + this.input + ((this.blink / 8) % 2 == 0 ? "_" : " ");
		List<String> inputWrapped = this.wrap(inputLine, this.w - 8);
		int total = wrapped.size();
		int maxScroll = Math.max(0, total - (rows - inputWrapped.size() + 1));
		this.scroll = Math.min(this.scroll, maxScroll);
		int start = Math.max(0, total - (rows - inputWrapped.size() + 1) - this.scroll);
		int yy = this.y + 3;
		for (int i = start; i < total && yy < this.y + this.h - lineH * inputWrapped.size(); i++) {
			String l = wrapped.get(i);
			int col = l.startsWith("> ") ? t.accent() : l.startsWith("Error") || l.startsWith("Unknown") ? 0xFFFF6B6B
				: l.startsWith("[ch ") ? 0xFF7FD3FF : t.text();
			g.text(this.font(), l, this.x + 4, yy, col, false);
			yy += lineH;
		}
		for (String l : inputWrapped) {
			g.text(this.font(), l, this.x + 4, yy, t.accent(), false);
			yy += lineH;
		}
		if (this.scroll > 0) g.text(this.font(), "^ scrolled (" + this.scroll + ")", this.x + this.w - 90, this.y + 3, 0x90FFFFFF, false);
	}

	private List<String> wrap(String s, int maxW) {
		List<String> out = new ArrayList<>();
		if (s.isEmpty()) {
			out.add("");
			return out;
		}
		s = s.replace('\t', ' ');
		while (!s.isEmpty()) {
			int n = s.length();
			while (n > 1 && this.font().width(s.substring(0, n)) > maxW) n--;
			out.add(s.substring(0, n));
			s = s.substring(n);
		}
		return out;
	}

	@Override
	public boolean mouseScrolled(double amount) {
		this.scroll = Math.max(0, this.scroll + (int) Math.signum(amount) * 3);
		return true;
	}

	@Override
	public boolean charTyped(CharacterEvent e) {
		if (e.isAllowedChatCharacter() && this.input.length() < 200) {
			this.input.append(e.codepointAsString());
			this.blink = 0;
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		switch (e.key()) {
			case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
				String line = this.input.toString().trim();
				this.input.setLength(0);
				this.historyPos = -1;
				this.scroll = 0;
				if (!line.isEmpty()) {
					this.history.add(line);
					this.submit(line);
				}
				return true;
			}
			case InputConstants.KEY_BACKSPACE -> {
				if (this.input.length() > 0) this.input.setLength(this.input.length() - 1);
				return true;
			}
			case InputConstants.KEY_UP -> {
				if (!this.history.isEmpty()) {
					this.historyPos = this.historyPos < 0 ? this.history.size() - 1 : Math.max(0, this.historyPos - 1);
					this.input.setLength(0);
					this.input.append(this.history.get(this.historyPos));
				}
				return true;
			}
			case InputConstants.KEY_DOWN -> {
				if (this.historyPos >= 0) {
					this.historyPos++;
					this.input.setLength(0);
					if (this.historyPos < this.history.size()) this.input.append(this.history.get(this.historyPos));
					else this.historyPos = -1;
				}
				return true;
			}
			default -> {
				return false;
			}
		}
	}

	/** A few commands open apps right here; everything else runs on the computer. */
	private void submit(String line) {
		String[] p = line.split("\\s+", 2);
		String cmd = p[0].toLowerCase();
		String arg = p.length > 1 ? p[1].trim() : "";
		switch (cmd) {
			case "edit", "notepad", "nano" -> {
				if (arg.isEmpty()) this.os.openApp("Notepad");
				else this.os.openFile(arg);
			}
			case "paint" -> {
				if (arg.isEmpty()) this.os.openApp("Paint");
				else this.os.openFile(arg.endsWith(".pix") ? arg : arg + ".pix");
			}
			case "snake", "clock", "files", "settings", "messenger" -> this.os.openApp(cmd);
			case "calc" -> {
				if (arg.isEmpty()) this.os.openApp("Calc");
				else this.os.runCommand(line);
			}
			case "exit", "quit", "logout" -> this.os.onClose();
			default -> this.os.runCommand(line);
		}
	}
}
