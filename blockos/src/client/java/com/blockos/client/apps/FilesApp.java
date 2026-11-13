package com.blockos.client.apps;

import com.blockos.client.screen.ComputerScreen;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;

public class FilesApp extends App {
	private @Nullable String selected;
	private int scroll;

	public FilesApp(ComputerScreen os) {
		super(os);
	}

	@Override
	public String title() {
		return "Files";
	}

	@Override
	public void drawIcon(GuiGraphicsExtractor g, int ix, int iy) {
		g.fill(ix + 1, iy + 3, ix + 7, iy + 5, 0xFFE0A93B);
		g.fill(ix + 1, iy + 5, ix + 15, iy + 14, 0xFFF5C451);
		g.fill(ix + 1, iy + 5, ix + 15, iy + 6, 0xFFFFE08A);
	}

	private List<String> list() {
		List<String> l = new ArrayList<>();
		for (String f : this.os.files().keySet()) if (!f.startsWith(".")) l.add(f);
		l.sort(String::compareTo);
		return l;
	}

	@Override
	public void render(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		ComputerScreen.Theme t = this.os.theme();
		List<String> files = this.list();
		int rowH = 11;
		int listH = this.h - 22;
		int visible = listH / rowH;
		this.scroll = Math.max(0, Math.min(this.scroll, Math.max(0, files.size() - visible)));
		int used = 0;
		for (var e : this.os.files().entrySet()) used += e.getKey().length() + e.getValue().length();
		g.text(this.font(), files.size() + " files, " + used / 1024 + "." + (used % 1024) * 10 / 1024 + " KB of 64 KB", this.x + 4, this.y + 3, 0xA0FFFFFF, false);
		for (int i = 0; i < visible && i + this.scroll < files.size(); i++) {
			String f = files.get(i + this.scroll);
			int ry = this.y + 14 + i * rowH;
			boolean sel = f.equals(this.selected);
			boolean hover = this.inside(mouseX, mouseY, this.x + 2, ry, this.w - 4, rowH);
			if (sel) g.fill(this.x + 2, ry, this.x + this.w - 2, ry + rowH, t.accentDim());
			else if (hover) g.fill(this.x + 2, ry, this.x + this.w - 2, ry + rowH, 0x30FFFFFF);
			int iconCol = f.endsWith(".bs") ? 0xFF7CFF9E : f.endsWith(".pix") ? 0xFFFF8AD8 : 0xFFB8C4D6;
			g.fill(this.x + 6, ry + 2, this.x + 12, ry + 9, iconCol);
			g.text(this.font(), f, this.x + 16, ry + 2, t.text(), false);
			String size = this.os.files().get(f).length() + " B";
			g.text(this.font(), size, this.x + this.w - 8 - this.font().width(size), ry + 2, 0x90FFFFFF, false);
		}
		int by = this.y + this.h - 15;
		this.button(g, this.x + 4, by, 44, 13, "Open", mouseX, mouseY);
		this.button(g, this.x + 52, by, 44, 13, "Run", mouseX, mouseY);
		this.button(g, this.x + 100, by, 44, 13, "Delete", mouseX, mouseY);
		this.button(g, this.x + 148, by, 52, 13, "New file", mouseX, mouseY);
		if (this.selected == null) g.text(this.font(), "Click a file, double-click to open", this.x + 206, by + 3, 0x80FFFFFF, false);
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button, boolean doubleClick) {
		List<String> files = this.list();
		int rowH = 11;
		int by = this.y + this.h - 15;
		if (this.inside(mx, my, this.x + 4, by, 44, 13) && this.selected != null) this.os.openFile(this.selected);
		else if (this.inside(mx, my, this.x + 52, by, 44, 13) && this.selected != null && this.selected.endsWith(".bs")) {
			this.os.runCommand("run " + this.selected);
			this.os.openApp("Terminal");
		} else if (this.inside(mx, my, this.x + 100, by, 44, 13) && this.selected != null) {
			this.os.deleteFile(this.selected);
			this.selected = null;
		} else if (this.inside(mx, my, this.x + 148, by, 52, 13)) {
			this.os.app(NotepadApp.class).newFile();
			this.os.openApp(this.os.app(NotepadApp.class));
		} else {
			int i = (int) ((my - this.y - 14) / rowH) + this.scroll;
			if (my >= this.y + 14 && i >= 0 && i < files.size()) {
				String f = files.get(i);
				if (doubleClick && f.equals(this.selected)) this.os.openFile(f);
				this.selected = f;
			}
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double amount) {
		this.scroll -= (int) Math.signum(amount) * 2;
		return true;
	}
}
