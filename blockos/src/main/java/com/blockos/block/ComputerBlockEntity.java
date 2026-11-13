package com.blockos.block;

import com.blockos.BlockOS;
import com.blockos.net.Net;
import com.blockos.shell.Script;
import com.blockos.shell.Shell;
import com.blockos.shell.Wireless;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Everything a computer remembers: its files, terminal, redstone outputs, name, wireless channel
 * and the program it's running. Programs run on the server every tick, even with the screen closed.
 */
public class ComputerBlockEntity extends BlockEntity {
	public static final int MAX_LINES = 200;
	public static final int MAX_STORAGE = 64 * 1024;
	private static final Gson GSON = new Gson();

	private final LinkedHashMap<String, String> files = new LinkedHashMap<>();
	private final List<String> terminal = new ArrayList<>();
	private final int[] outputs = new int[6];
	private String label = "";
	private int channel = -1;
	private final ArrayDeque<String> inbox = new ArrayDeque<>();
	private @Nullable Script script;
	private String scriptName = "";
	private final Set<UUID> viewers = new HashSet<>();
	private boolean needsSync;
	private boolean booted;
	private long bootTick;

	public ComputerBlockEntity(BlockPos pos, BlockState state) {
		super(BlockOS.COMPUTER_BE, pos, state);
	}

	// ------------------------------------------------------------------ ticking

	public static void serverTick(Level level, BlockPos pos, BlockState state, ComputerBlockEntity c) {
		c.tick((ServerLevel) level);
	}

	private void tick(ServerLevel level) {
		if (!this.booted) {
			this.boot(level);
		}
		if (this.script != null) {
			this.script.tick(this, level);
			if (this.script.isDone()) {
				this.script = null;
				this.scriptName = "";
				this.markSync();
			}
		}
		if (this.needsSync) {
			this.needsSync = false;
			this.syncToViewers(level, false);
		}
	}

	private void boot(ServerLevel level) {
		this.booted = true;
		this.bootTick = level.getGameTime();
		if (this.files.isEmpty()) {
			DefaultFiles.install(this.files);
			this.print("BlockOS 1.0 ready. Type 'help' to see what you can do.");
		}
		if (this.channel >= 0) {
			Wireless.listen(level, this.worldPosition, this.channel);
		}
		// Resume a program that was running when the world was saved, otherwise run startup.bs
		String resume = !this.scriptName.isEmpty() ? this.scriptName : (this.files.containsKey("startup.bs") ? "startup.bs" : "");
		if (!resume.isEmpty() && this.files.containsKey(resume)) {
			this.startScript(resume, level);
		}
		this.setChanged();
	}

	public static void onRemoved(ServerLevel level, BlockPos pos) {
		Wireless.forget(level, pos);
	}

	// ------------------------------------------------------------------ screen + sync

	public void openFor(ServerPlayer player) {
		this.viewers.add(player.getUUID());
		this.syncTo(player, true);
	}

	public void closeFor(ServerPlayer player) {
		this.viewers.remove(player.getUUID());
	}

	private void markSync() {
		this.needsSync = true;
		this.setChanged();
	}

	private void syncToViewers(ServerLevel level, boolean open) {
		this.viewers.removeIf(id -> {
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
			if (p == null || p.level() != level || p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(this.worldPosition)) > 64.0) return true;
			this.syncTo(p, open);
			return false;
		});
	}

	private void syncTo(ServerPlayer player, boolean open) {
		Net.sendSync(player, this.worldPosition, this.label, GSON.toJson(this.files), GSON.toJson(this.terminal),
			this.script != null ? this.scriptName : "", this.channel, open);
	}

	// ------------------------------------------------------------------ terminal

	public void print(String text) {
		for (String line : text.split("\n", -1)) {
			this.terminal.add(line);
		}
		while (this.terminal.size() > MAX_LINES) {
			this.terminal.remove(0);
		}
		this.markSync();
	}

	public void clearTerminal() {
		this.terminal.clear();
		this.markSync();
	}

	public void runCommand(ServerLevel level, @Nullable ServerPlayer player, String line) {
		this.print("> " + line);
		Shell.execute(this, level, player, line, null);
	}

	// ------------------------------------------------------------------ programs

	public boolean startScript(String name, ServerLevel level) {
		String src = this.files.get(name);
		if (src == null) {
			this.print("No such file: " + name);
			return false;
		}
		try {
			this.script = Script.compile(src, name);
		} catch (Script.ScriptError e) {
			this.print("Error in " + name + ": " + e.getMessage());
			this.script = null;
			return false;
		}
		this.scriptName = name;
		this.markSync();
		return true;
	}

	public boolean stopScript() {
		boolean was = this.script != null;
		this.script = null;
		this.scriptName = "";
		this.markSync();
		return was;
	}

	public @Nullable String runningScript() {
		return this.script != null ? this.scriptName : null;
	}

	// ------------------------------------------------------------------ files

	public Map<String, String> files() {
		return this.files;
	}

	public int usedStorage() {
		int n = 0;
		for (Map.Entry<String, String> e : this.files.entrySet()) n += e.getKey().length() + e.getValue().length();
		return n;
	}

	/** Returns an error message, or null on success. */
	public @Nullable String writeFile(String path, String content) {
		path = cleanPath(path);
		if (path.isEmpty()) return "Bad file name";
		String old = this.files.get(path);
		int newSize = this.usedStorage() - (old == null ? 0 : path.length() + old.length()) + path.length() + content.length();
		if (newSize > MAX_STORAGE) return "Disk full (" + MAX_STORAGE / 1024 + " KB limit)";
		this.files.put(path, content);
		this.markSync();
		return null;
	}

	public boolean deleteFile(String path) {
		boolean removed = this.files.remove(cleanPath(path)) != null;
		if (removed) this.markSync();
		return removed;
	}

	public static String cleanPath(String path) {
		path = path.trim().replace('\\', '/');
		while (path.startsWith("/")) path = path.substring(1);
		path = path.replaceAll("[^A-Za-z0-9_./ -]", "").replaceAll("/+", "/");
		if (path.contains("..") || path.length() > 64) return "";
		return path;
	}

	// ------------------------------------------------------------------ redstone

	public Direction facing() {
		return this.getBlockState().getValue(ComputerBlock.FACING);
	}

	/** front/back/left/right/top/bottom as seen by someone looking at the screen. */
	public @Nullable Direction side(String name) {
		Direction f = this.facing();
		return switch (name.toLowerCase()) {
			case "front" -> f;
			case "back" -> f.getOpposite();
			case "left" -> f.getClockWise();
			case "right" -> f.getCounterClockWise();
			case "top", "up" -> Direction.UP;
			case "bottom", "down" -> Direction.DOWN;
			default -> null;
		};
	}

	public static final String[] SIDES = {"front", "back", "left", "right", "top", "bottom"};

	public int getOutput(Direction d) {
		return this.outputs[d.get3DDataValue()];
	}

	public void setOutput(ServerLevel level, Direction d, int power) {
		power = Math.max(0, Math.min(15, power));
		if (this.outputs[d.get3DDataValue()] == power) return;
		this.outputs[d.get3DDataValue()] = power;
		this.setChanged();
		level.updateNeighborsAt(this.worldPosition, this.getBlockState().getBlock());
		level.updateNeighborsAt(this.worldPosition.relative(d), this.getBlockState().getBlock());
	}

	public int getInput(ServerLevel level, Direction d) {
		return level.getSignal(this.worldPosition.relative(d), d);
	}

	// ------------------------------------------------------------------ wireless

	public void setChannel(ServerLevel level, int ch) {
		Wireless.forget(level, this.worldPosition);
		this.channel = ch;
		if (ch >= 0) Wireless.listen(level, this.worldPosition, ch);
		this.markSync();
	}

	public int channel() {
		return this.channel;
	}

	public void receive(int ch, String from, String msg) {
		this.inbox.add(msg);
		while (this.inbox.size() > 50) this.inbox.poll();
		this.print("[ch " + ch + " from " + from + "] " + msg);
	}

	public @Nullable String popMessage() {
		return this.inbox.poll();
	}

	// ------------------------------------------------------------------ misc

	public String label() {
		return this.label.isEmpty() ? "Computer " + this.worldPosition.toShortString() : this.label;
	}

	public void setLabel(String label) {
		this.label = label.length() > 32 ? label.substring(0, 32) : label;
		this.markSync();
	}

	public long uptimeTicks(ServerLevel level) {
		return level.getGameTime() - this.bootTick;
	}

	// ------------------------------------------------------------------ save / load

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putString("Files", GSON.toJson(this.files));
		output.putString("Terminal", GSON.toJson(this.terminal));
		output.putIntArray("Outputs", this.outputs);
		output.putString("Label", this.label);
		output.putInt("Channel", this.channel);
		output.putString("Program", this.script != null ? this.scriptName : "");
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.files.clear();
		Map<String, String> f = GSON.fromJson(input.getStringOr("Files", "{}"), new TypeToken<LinkedHashMap<String, String>>() {}.getType());
		if (f != null) this.files.putAll(f);
		this.terminal.clear();
		List<String> t = GSON.fromJson(input.getStringOr("Terminal", "[]"), new TypeToken<ArrayList<String>>() {}.getType());
		if (t != null) this.terminal.addAll(t);
		int[] o = input.getIntArray("Outputs").orElse(new int[6]);
		System.arraycopy(o, 0, this.outputs, 0, Math.min(6, o.length));
		this.label = input.getStringOr("Label", "");
		this.channel = input.getIntOr("Channel", -1);
		this.scriptName = input.getStringOr("Program", "");
		this.booted = false;
	}
}
