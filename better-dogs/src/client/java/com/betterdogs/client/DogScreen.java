package com.betterdogs.client;

import com.betterdogs.DogSettings;
import com.betterdogs.Net;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.DyeColor;

/** The dog's settings screen: sneak + right-click your dog. */
public class DogScreen extends Screen {
	private static final int W = 330, H = 212;
	private static final int ACCENT = 0xFFE0A050, PANEL = 0xF0201814, EDGE = 0xFF6B4A2E, TEXT = 0xFFF2E6D8, DIM = 0xFFB09A86;

	public final Wolf wolf;
	private DogSettings settings;
	private int left, top;

	private EditBox nameBox;
	private Button invincible, regen, collect, behavior, ownerSafe, swift, glow, danger, collar, sit;

	public DogScreen(Wolf wolf, DogSettings settings) {
		super(Component.literal("Dog settings"));
		this.wolf = wolf;
		this.settings = settings;
	}

	public DogSettings settings() {
		return this.settings;
	}

	private void send(String key, String value) {
		ClientPlayNetworking.send(new Net.Change(this.wolf.getId(), key, value));
	}

	/** New settings from the server. */
	public void apply(DogSettings s) {
		this.settings = s;
		this.refreshLabels();
	}

	private static Component onOff(String name, boolean on) {
		return Component.literal(name + ": ").append(Component.literal(on ? "ON" : "OFF").withColor(on ? 0x6BE06B : 0xE07A6B));
	}

	private void refreshLabels() {
		if (this.invincible == null) return;
		DogSettings s = this.settings;
		this.invincible.setMessage(onOff("Invincible", s.invincible()));
		this.regen.setMessage(onOff("Regeneration", s.regen()));
		this.collect.setMessage(Component.literal("Auto-collect: ").append(Component.literal(s.collect().label).withColor(s.collect() == DogSettings.Collect.OFF ? 0xE07A6B : 0xF5D06B)));
		this.collect.setTooltip(Tooltip.create(Component.literal("Picks up dropped items near you and brings them to your inventory.\n\n" + s.collect().label + ": " + s.collect().help)));
		this.behavior.setMessage(Component.literal("Behavior: ").append(Component.literal(s.behavior().label).withColor(0xF5D06B)));
		this.behavior.setTooltip(Tooltip.create(Component.literal(s.behavior().label + ": " + s.behavior().help)));
		this.ownerSafe.setMessage(onOff("Safe from you", s.ownerSafe()));
		this.swift.setMessage(onOff("Swift paws", s.swift()));
		this.glow.setMessage(onOff("Glow", s.glow()));
		this.danger.setMessage(onOff("Danger alert", s.dangerBark()));
		DyeColor c = this.wolf.getCollarColor();
		this.collar.setMessage(Component.literal("Collar: ").append(Component.literal("\u25A0 ").withColor(c.getTextureDiffuseColor() & 0xFFFFFF)).append(Component.literal(pretty(c))));
		this.sit.setMessage(Component.literal(this.wolf.isOrderedToSit() ? "Stand up" : "Sit"));
	}

	private static String pretty(DyeColor c) {
		String n = c.getName().replace('_', ' ');
		return Character.toUpperCase(n.charAt(0)) + n.substring(1);
	}

	private Button toggle(int x, int y, int w, String key, Function<DogSettings, Boolean> get, Function<Boolean, DogSettings> set, String help) {
		Button b = Button.builder(Component.empty(), btn -> {
			boolean next = !get.apply(this.settings);
			this.settings = set.apply(next);
			this.send(key, Boolean.toString(next));
			this.refreshLabels();
		}).bounds(x, y, w, 20).tooltip(Tooltip.create(Component.literal(help))).build();
		return this.addRenderableWidget(b);
	}

	@Override
	protected void init() {
		this.left = (this.width - W) / 2;
		this.top = (this.height - H) / 2;
		int x = this.left + 112, y = this.top + 22, full = W - 112 - 8, half = (full - 4) / 2;

		this.nameBox = new EditBox(this.font, x, y, full - 54, 20, Component.literal("Name"));
		this.nameBox.setMaxLength(32);
		this.nameBox.setHint(Component.literal("Give your dog a name").withColor(0xFF8A7A6A));
		this.nameBox.setValue(this.wolf.hasCustomName() ? this.wolf.getCustomName().getString() : "");
		this.addRenderableWidget(this.nameBox);
		this.addRenderableWidget(Button.builder(Component.literal("Rename"), b -> this.send("name", this.nameBox.getValue()))
			.bounds(x + full - 50, y, 50, 20).build());
		y += 26;

		this.invincible = this.toggle(x, y, half, "invincible", DogSettings::invincible, v -> this.settings.withInvincible(v),
			"Nothing can hurt your dog: mobs, lava, falling, drowning, explosions. Only /kill and the void still work.");
		this.regen = this.toggle(x + half + 4, y, half, "regen", DogSettings::regen, v -> this.settings.withRegen(v),
			"Heals half a heart every 2 seconds.");
		y += 24;
		this.collect = this.addRenderableWidget(Button.builder(Component.empty(), b -> {
			DogSettings.Collect[] all = DogSettings.Collect.values();
			DogSettings.Collect next = all[(this.settings.collect().ordinal() + (hasShiftDown() ? all.length - 1 : 1)) % all.length];
			this.settings = this.settings.withCollect(next);
			this.send("collect", next.name());
			this.refreshLabels();
		}).bounds(x, y, full, 20).build());
		y += 24;
		this.behavior = this.addRenderableWidget(Button.builder(Component.empty(), b -> {
			DogSettings.Behavior[] all = DogSettings.Behavior.values();
			DogSettings.Behavior next = all[(this.settings.behavior().ordinal() + 1) % all.length];
			this.settings = this.settings.withBehavior(next);
			this.send("behavior", next.name());
			this.refreshLabels();
		}).bounds(x, y, full, 20).build());
		y += 24;
		this.ownerSafe = this.toggle(x, y, half, "owner_safe", DogSettings::ownerSafe, v -> this.settings.withOwnerSafe(v),
			"Your own hits, arrows and explosions can't hurt your dog by accident.");
		this.swift = this.toggle(x + half + 4, y, half, "swift", DogSettings::swift, v -> this.settings.withSwift(v),
			"Runs 40% faster, so it keeps up when you sprint.");
		y += 24;
		this.glow = this.toggle(x, y, half, "glow", DogSettings::glow, v -> this.settings.withGlow(v),
			"Outlined through walls so you never lose your dog.");
		this.danger = this.toggle(x + half + 4, y, half, "danger_bark", DogSettings::dangerBark, v -> this.settings.withDangerBark(v),
			"Barks and makes hostile mobs near you glow for a few seconds, even behind walls.");
		y += 24;
		int third = (full - 8) / 3;
		this.collar = this.addRenderableWidget(Button.builder(Component.empty(), b -> {
			DyeColor[] all = DyeColor.values();
			DyeColor next = all[(this.wolf.getCollarColor().ordinal() + (hasShiftDown() ? all.length - 1 : 1)) % all.length];
			this.send("collar", next.getName());
		}).bounds(x, y, third + 20, 20).tooltip(Tooltip.create(Component.literal("Click for the next color, shift-click to go back."))).build());
		this.sit = this.addRenderableWidget(Button.builder(Component.empty(), b -> this.send("sit", Boolean.toString(!this.wolf.isOrderedToSit())))
			.bounds(x + third + 24, y, third - 10, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Call to me"), b -> this.send("call", ""))
			.bounds(x + 2 * third + 18, y, full - (2 * third + 18), 20)
			.tooltip(Tooltip.create(Component.literal("Teleports your dog to you."))).build());
		this.refreshLabels();
	}

	private static boolean hasShiftDown() {
		return net.minecraft.client.Minecraft.getInstance().hasShiftDown();
	}

	@Override
	public void tick() {
		if (!this.wolf.isAlive() || this.wolf.isRemoved() || this.minecraft.player == null || this.minecraft.player.distanceToSqr(this.wolf) > 64 * 64) {
			this.onClose();
			return;
		}
		this.refreshLabels();   // collar / sitting come from the dog itself
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (this.nameBox.isFocused() && (e.key() == InputConstants.KEY_RETURN || e.key() == InputConstants.KEY_NUMPADENTER)) {
			this.send("name", this.nameBox.getValue());
			this.nameBox.setFocused(false);
			return true;
		}
		return super.keyPressed(e);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.fill(0, 0, this.width, this.height, 0x90000000);
		int l = this.left, t = this.top;
		g.fill(l - 2, t - 2, l + W + 2, t + H + 2, 0xFF0E0A08);
		g.fill(l, t, l + W, t + H, PANEL);
		g.outline(l, t, W, H, EDGE);
		g.fill(l + 1, t + 1, l + W - 1, t + 16, 0xFF3A2A1E);
		// Dog portrait area
		g.fill(l + 6, t + 22, l + 106, t + 126, 0xFF120D0A);
		g.outline(l + 6, t + 22, 100, 104, EDGE);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		int l = this.left, t = this.top;
		String name = this.wolf.hasCustomName() ? this.wolf.getCustomName().getString() : "Your dog";
		g.text(this.font, name, l + 7, t + 5, ACCENT, false);
		String hint = "Better Dogs";
		g.text(this.font, hint, l + W - this.font.width(hint) - 7, t + 5, DIM, false);

		InventoryScreen.extractEntityInInventoryFollowsMouse(g, l + 7, t + 23, l + 105, t + 125, 52, 0.0625F, mouseX, mouseY, this.wolf);

		// Health bar
		int bx = l + 6, by = t + 132, bw = 100;
		float hp = this.wolf.getHealth(), max = Math.max(1, this.wolf.getMaxHealth());
		g.fill(bx, by, bx + bw, by + 9, 0xFF120D0A);
		int fillColor = this.settings.invincible() ? 0xFFF5D06B : (hp / max > 0.5 ? 0xFF5BC85B : hp / max > 0.25 ? 0xFFE0B040 : 0xFFE05050);
		g.fill(bx + 1, by + 1, bx + 1 + Math.round((bw - 2) * Math.min(1, hp / max)), by + 8, fillColor);
		String hpText = this.settings.invincible() ? "Invincible" : String.format("%.0f / %.0f", hp, max);
		g.centeredText(this.font, hpText, bx + bw / 2, by + 1, 0xFFFFFFFF);

		int sy = by + 14;
		g.text(this.font, "Items fetched: " + this.settings.collected(), bx, sy, TEXT, false);
		g.text(this.font, "Mobs defeated: " + this.settings.kills(), bx, sy + 11, TEXT, false);
		g.text(this.font, this.wolf.isOrderedToSit() ? "Sitting" : "Following you", bx, sy + 22, DIM, false);
		g.fill(bx, sy + 34, bx + 8, sy + 42, 0xFF000000 | this.wolf.getCollarColor().getTextureDiffuseColor());
		g.text(this.font, "Collar", bx + 11, sy + 34, DIM, false);

		super.extractRenderState(g, mouseX, mouseY, a);
	}
}
