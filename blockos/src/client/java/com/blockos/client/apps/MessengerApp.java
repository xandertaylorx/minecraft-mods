package com.blockos.client.apps;

import com.blockos.client.screen.ComputerScreen;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

/** Wireless chat between computers. Pick a channel, type, press Enter. */
public class MessengerApp extends App {
	private final StringBuilder input = new StringBuilder();
	private int channel = 1;
	private int blink;

	public MessengerApp(ComputerScreen os) {
		super(os);
	}

	@Override
	public String title() {
		return "Messenger";
	}

	@Override
	public void drawIcon(GuiGraphicsExtractor g, int ix, int iy) {
		g.fill(ix + 1, iy + 3, ix + 12, iy + 10, 0xFF4FC3F7);
		g.fill(ix + 3, iy + 10, ix + 5, iy + 13, 0xFF4FC3F7);
		g.fill(ix + 5, iy + 6, ix + 15, iy + 13, 0xFFFFFFFF);
		g.fill(ix + 12, iy + 13, ix + 14, iy + 15, 0xFFFFFFFF);
	}

	@Override
	public void onSync() {
		if (this.os.channel() >= 0) this.channel = this.os.channel();
	}

	@Override
	public void tick() {
		this.blink++;
	}

	@Override
	public void render(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		ComputerScreen.Theme t = this.os.theme();
		boolean listening = this.os.channel() == this.channel;
		g.text(this.font(), "Channel", this.x + 4, this.y + 4, t.text(), false);
		this.button(g, this.x + 46, this.y + 2, 12, 12, "-", mouseX, mouseY);
		g.text(this.font(), String.valueOf(this.channel), this.x + 64, this.y + 4, t.accent(), false);
		this.button(g, this.x + 80, this.y + 2, 12, 12, "+", mouseX, mouseY);
		this.button(g, this.x + 98, this.y + 2, 70, 12, listening ? "Listening" : "Listen", mouseX, mouseY);
		g.text(this.font(), "Any computer on the same channel gets your messages.", this.x + 174, this.y + 4, 0x70FFFFFF, false);

		// Conversation: messages received + messages we sent, pulled from the terminal log
		List<String> convo = new ArrayList<>();
		for (String l : this.os.terminal()) {
			if (l.startsWith("[ch ")) convo.add(l);
			else if (l.startsWith("> send ")) convo.add("me: " + l.substring(7).replaceFirst("^\\d+\\s*", ""));
		}
		int lineH = 11;
		int area = this.h - 36;
		int rows = area / lineH;
		int yy = this.y + 20;
		g.fill(this.x + 2, yy - 2, this.x + this.w - 2, yy + area, 0xFF0A0D10);
		for (int i = Math.max(0, convo.size() - rows); i < convo.size(); i++) {
			String l = convo.get(i);
			boolean mine = l.startsWith("me: ");
			int bw = Math.min(this.w - 20, this.font().width(l) + 8);
			int bx = mine ? this.x + this.w - 6 - bw : this.x + 6;
			g.fill(bx, yy, bx + bw, yy + 10, mine ? t.accentDim() : 0xFF22303C);
			g.text(this.font(), l, bx + 4, yy + 1, mine ? 0xFFFFFFFF : 0xFFBFE6FF, false);
			yy += lineH;
		}
		if (convo.isEmpty()) g.centeredText(this.font(), "No messages yet. Press Listen, then say hi!", this.x + this.w / 2, this.y + 20 + area / 2 - 4, 0x70FFFFFF);
		int iy = this.y + this.h - 14;
		g.fill(this.x + 2, iy, this.x + this.w - 2, iy + 12, 0xFF151A1F);
		g.outline(this.x + 2, iy, this.w - 4, 12, t.accentDim());
		String shown = this.input.isEmpty() ? "Type a message and press Enter" : this.input.toString();
		g.text(this.font(), shown + (!this.input.isEmpty() && (this.blink / 8) % 2 == 0 ? "_" : ""), this.x + 6, iy + 2,
			this.input.isEmpty() ? 0x60FFFFFF : t.text(), false);
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button, boolean doubleClick) {
		if (this.inside(mx, my, this.x + 46, this.y + 2, 12, 12)) this.channel = Math.max(0, this.channel - 1);
		else if (this.inside(mx, my, this.x + 80, this.y + 2, 12, 12)) this.channel = Math.min(9999, this.channel + 1);
		else if (this.inside(mx, my, this.x + 98, this.y + 2, 70, 12)) this.os.runCommand("listen " + this.channel);
		return true;
	}

	@Override
	public boolean charTyped(CharacterEvent e) {
		if (e.isAllowedChatCharacter() && this.input.length() < 120) this.input.append(e.codepointAsString());
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (e.key() == InputConstants.KEY_RETURN && !this.input.isEmpty()) {
			if (this.os.channel() != this.channel) this.os.runCommand("listen " + this.channel);
			this.os.runCommand("send " + this.channel + " " + this.input);
			this.input.setLength(0);
			return true;
		}
		if (e.key() == InputConstants.KEY_BACKSPACE && !this.input.isEmpty()) {
			this.input.setLength(this.input.length() - 1);
			return true;
		}
		return false;
	}
}
