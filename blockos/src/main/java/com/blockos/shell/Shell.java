package com.blockos.shell;

import com.blockos.block.ComputerBlockEntity;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import org.jspecify.annotations.Nullable;

/** Every terminal command, shared by the Terminal app and BlockScript programs. */
public final class Shell {
	private Shell() {
	}

	public static final String HELP = String.join("\n",
		"--- Files ---",
		"ls [folder]          list files",
		"cat <file>           show a file",
		"write <file> <text>  create/replace a file      append <file> <text>",
		"rm <file>   mv <a> <b>   cp <a> <b>   edit <file> (opens Notepad)",
		"--- Programs (BlockScript) ---",
		"run <program>        start a program (see the programs/ folder)",
		"stop                 stop the running program    ps   what's running",
		"--- Redstone & world ---",
		"redstone <side> <on|off|0-15>   sides: front back left right top bottom",
		"input [side]         read redstone coming in",
		"scan                 what blocks are around me",
		"players [range]      who is nearby    weather    time",
		"play <instrument> <0-24>   note sounds (harp bell flute chime guitar bit...)",
		"say <message>        message nearby players in chat",
		"--- Wireless ---",
		"listen <channel>|off   send <channel> <message>   inbox",
		"--- Other ---",
		"calc <maths>   echo <text>   date   label [name]   clear   reboot   about",
		"Apps: notepad, paint, snake, calc, clock, files, settings (or use the desktop)");

	// ------------------------------------------------------------------ functions for expressions

	public static Expr.Functions functions(ComputerBlockEntity c, ServerLevel level) {
		return (name, args) -> switch (name) {
			case "input" -> (double) c.getInput(level, sideOrThrow(c, args, 0));
			case "output" -> (double) c.getOutput(sideOrThrow(c, args, 0));
			case "time" -> (double) (level.getOverworldClockTime() % 24000L);
			case "day" -> isDay(level) ? 1.0 : 0.0;
			case "night" -> isDay(level) ? 0.0 : 1.0;
			case "raining" -> level.isRaining() ? 1.0 : 0.0;
			case "thundering" -> level.isThundering() ? 1.0 : 0.0;
			case "players" -> (double) nearbyPlayers(c, level, args.isEmpty() ? 16 : Expr.num(args.get(0))).size();
			case "random" -> (double) (1 + level.getRandom().nextInt(Math.max(1, (int) Expr.num(arg(args, 0, "random")))));
			case "message" -> {
				String m = c.popMessage();
				yield m == null ? "" : m;
			}
			case "block" -> blockAt(c, level, args.isEmpty() ? "front" : args.get(0).toString());
			case "len" -> (double) Expr.str(arg(args, 0, "len")).length();
			case "word" -> {
				String[] words = Expr.str(arg(args, 0, "word")).trim().split("\\s+");
				int n = (int) Expr.num(arg(args, 1, "word"));
				yield n >= 0 && n < words.length ? words[n] : "";
			}
			case "str" -> Expr.str(arg(args, 0, "str"));
			case "num" -> Expr.num(arg(args, 0, "num"));
			case "round" -> (double) Math.round(Expr.num(arg(args, 0, "round")));
			case "floor" -> Math.floor(Expr.num(arg(args, 0, "floor")));
			case "sqrt" -> Math.sqrt(Expr.num(arg(args, 0, "sqrt")));
			case "abs" -> Math.abs(Expr.num(arg(args, 0, "abs")));
			case "min" -> Math.min(Expr.num(arg(args, 0, "min")), Expr.num(arg(args, 1, "min")));
			case "max" -> Math.max(Expr.num(arg(args, 0, "max")), Expr.num(arg(args, 1, "max")));
			case "sin" -> Math.sin(Math.toRadians(Expr.num(arg(args, 0, "sin"))));
			case "cos" -> Math.cos(Math.toRadians(Expr.num(arg(args, 0, "cos"))));
			case "file" -> {
				String f = c.files().get(ComputerBlockEntity.cleanPath(Expr.str(arg(args, 0, "file"))));
				yield f == null ? "" : f;
			}
			default -> throw new Expr.ExprError("Unknown function " + name + "()");
		};
	}

	private static Object arg(List<Object> args, int i, String fn) {
		if (args.size() <= i) throw new Expr.ExprError(fn + "() needs more values");
		return args.get(i);
	}

	private static Direction sideOrThrow(ComputerBlockEntity c, List<Object> args, int i) {
		String s = Expr.str(arg(args, i, "side"));
		Direction d = c.side(s);
		if (d == null) throw new Expr.ExprError("'" + s + "' isn't a side (front back left right top bottom)");
		return d;
	}

	private static boolean isDay(ServerLevel level) {
		long t = level.getOverworldClockTime() % 24000L;
		return t < 12300L || t > 23850L;
	}

	/** Replaces $name and ${expression} in a command line. */
	private static final Pattern VAR = Pattern.compile("\\$\\{([^}]*)}|\\$([A-Za-z_][A-Za-z0-9_]*)");

	public static String interpolate(String line, Map<String, Object> vars, Expr.Functions fns) {
		if (line.indexOf('$') < 0) return line;
		Matcher m = VAR.matcher(line);
		StringBuilder sb = new StringBuilder();
		while (m.find()) {
			String rep;
			if (m.group(1) != null) rep = Expr.str(Expr.eval(m.group(1), vars, fns));
			else {
				Object v = vars.get(m.group(2));
				rep = v == null ? m.group() : Expr.str(v);
			}
			m.appendReplacement(sb, Matcher.quoteReplacement(rep));
		}
		m.appendTail(sb);
		return sb.toString();
	}

	// ------------------------------------------------------------------ commands

	public static void execute(ComputerBlockEntity c, ServerLevel level, @Nullable ServerPlayer player, String line, @Nullable Script script) {
		line = line.trim();
		if (line.isEmpty()) return;
		String[] parts = line.split("\\s+");
		String cmd = parts[0].toLowerCase(Locale.ROOT);
		String rest = line.length() > parts[0].length() ? line.substring(parts[0].length()).trim() : "";
		try {
			switch (cmd) {
				case "help", "?" -> c.print(HELP);
				case "clear", "cls" -> c.clearTerminal();
				case "echo" -> c.print(rest);
				case "about", "ver", "version" -> c.print("BlockOS 1.0  -  " + c.files().size() + " files, " + c.usedStorage() / 1024 + "/" + ComputerBlockEntity.MAX_STORAGE / 1024 + " KB used");
				case "label", "name" -> {
					if (rest.isEmpty()) c.print("This computer is called: " + c.label());
					else {
						c.setLabel(rest);
						c.print("Renamed to " + rest);
					}
				}
				case "date" -> c.print(LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, HH:mm:ss")));
				case "time" -> {
					long t = level.getOverworldClockTime();
					long dayTime = t % 24000L;
					int hours = (int) ((dayTime / 1000 + 6) % 24);
					int mins = (int) (dayTime % 1000 * 60 / 1000);
					c.print(String.format("Day %d, %02d:%02d (%s)", t / 24000L + 1, hours, mins, isDay(level) ? "daytime" : "night"));
				}
				case "uptime" -> c.print("Running for " + c.uptimeTicks(level) / 20 + " seconds");
				case "calc", "=" -> c.print(rest + " = " + Expr.str(Expr.eval(rest, script != null ? script.vars() : new java.util.HashMap<>(), functions(c, level))));

				// ---- files ----
				case "ls", "dir", "files" -> {
					String prefix = rest.isEmpty() ? "" : ComputerBlockEntity.cleanPath(rest) + (rest.endsWith("/") ? "" : "/");
					TreeSet<String> shown = new TreeSet<>();
					for (String f : c.files().keySet()) {
						if (!f.startsWith(prefix)) continue;
						String tail = f.substring(prefix.length());
						int slash = tail.indexOf('/');
						shown.add(slash >= 0 ? tail.substring(0, slash + 1) : tail);
					}
					c.print(shown.isEmpty() ? "(empty)" : String.join("   ", shown));
				}
				case "cat", "type", "show" -> {
					String f = c.files().get(ComputerBlockEntity.cleanPath(rest));
					c.print(f == null ? "No such file: " + rest : f);
				}
				case "write", "append" -> {
					if (parts.length < 2) {
						c.print("Usage: " + cmd + " <file> <text>");
						return;
					}
					String path = parts[1];
					String text = line.substring(line.indexOf(parts[1]) + parts[1].length()).trim();
					String old = c.files().getOrDefault(ComputerBlockEntity.cleanPath(path), "");
					String err = c.writeFile(path, cmd.equals("append") && !old.isEmpty() ? old + "\n" + text : text);
					if (err != null) c.print(err);
					else if (script == null) c.print("Saved " + ComputerBlockEntity.cleanPath(path));
				}
				case "rm", "del", "delete" -> c.print(c.deleteFile(rest) ? "Deleted " + rest : "No such file: " + rest);
				case "mv", "cp", "rename", "copy" -> {
					if (parts.length < 3) {
						c.print("Usage: " + cmd + " <from> <to>");
						return;
					}
					String src = c.files().get(ComputerBlockEntity.cleanPath(parts[1]));
					if (src == null) {
						c.print("No such file: " + parts[1]);
						return;
					}
					String err = c.writeFile(parts[2], src);
					if (err != null) c.print(err);
					else {
						if (cmd.equals("mv") || cmd.equals("rename")) c.deleteFile(parts[1]);
						c.print("Done");
					}
				}

				// ---- programs ----
				case "run", "start" -> {
					String file = findProgram(c, rest);
					if (file == null) c.print("No program called '" + rest + "'. Try: ls programs");
					else if (script != null) c.print("A program can't start another program");
					else if (c.startScript(file, level)) c.print("Running " + file + "  (type 'stop' to end it)");
				}
				case "stop", "kill" -> {
					if (script != null) script.stop();
					else c.print(c.stopScript() ? "Stopped" : "Nothing is running");
				}
				case "ps" -> c.print(c.runningScript() == null ? "Nothing is running" : "Running: " + c.runningScript());
				case "reboot", "restart" -> {
					c.stopScript();
					c.clearTerminal();
					c.print("BlockOS 1.0 rebooted.");
					if (c.files().containsKey("startup.bs")) c.startScript("startup.bs", level);
				}

				// ---- redstone & world ----
				case "redstone", "rs", "output" -> {
					if (parts.length < 3) {
						StringBuilder sb = new StringBuilder("Outputs: ");
						for (String s : ComputerBlockEntity.SIDES) sb.append(s).append('=').append(c.getOutput(c.side(s))).append("  ");
						c.print(sb.toString().trim());
						return;
					}
					List<Direction> dirs = new ArrayList<>();
					if (parts[1].equalsIgnoreCase("all")) for (String s : ComputerBlockEntity.SIDES) dirs.add(c.side(s));
					else {
						Direction d = c.side(parts[1]);
						if (d == null) {
							c.print("'" + parts[1] + "' isn't a side. Use front back left right top bottom or all");
							return;
						}
						dirs.add(d);
					}
					int power = switch (parts[2].toLowerCase()) {
						case "on", "true", "yes" -> 15;
						case "off", "false", "no" -> 0;
						default -> (int) Expr.num(parts[2]);
					};
					for (Direction d : dirs) c.setOutput(level, d, power);
					if (script == null) c.print("Redstone " + parts[1] + " = " + Math.max(0, Math.min(15, power)));
				}
				case "input" -> {
					if (rest.isEmpty()) {
						StringBuilder sb = new StringBuilder("Inputs: ");
						for (String s : ComputerBlockEntity.SIDES) sb.append(s).append('=').append(c.getInput(level, c.side(s))).append("  ");
						c.print(sb.toString().trim());
					} else {
						Direction d = c.side(rest);
						c.print(d == null ? "Unknown side " + rest : rest + " = " + c.getInput(level, d));
					}
				}
				case "scan" -> {
					StringBuilder sb = new StringBuilder();
					for (String s : ComputerBlockEntity.SIDES) sb.append(String.format("%-7s %s%n", s, blockAt(c, level, s)));
					c.print(sb.toString().stripTrailing());
				}
				case "players", "who" -> {
					double r = rest.isEmpty() ? 16 : Expr.num(rest);
					List<ServerPlayer> ps = nearbyPlayers(c, level, r);
					if (ps.isEmpty()) c.print("Nobody within " + (int) r + " blocks");
					for (ServerPlayer p : ps) c.print(String.format("%s  (%.0f blocks away)", p.getName().getString(), Math.sqrt(p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(c.getBlockPos())))));
				}
				case "weather" -> c.print(level.isThundering() ? "Thunderstorm" : level.isRaining() ? "Raining" : "Clear skies");
				case "play", "note", "beep" -> {
					String inst = parts.length > 1 ? parts[1] : "pling";
					int note = parts.length > 2 ? (int) Expr.num(parts[2]) : 12;
					if (cmd.equals("beep")) {
						inst = "pling";
						note = parts.length > 1 ? (int) Expr.num(parts[1]) : 18;
					}
					NoteBlockInstrument instrument = instrument(inst);
					if (instrument == null) {
						c.print("Instruments: harp bass bell flute chime guitar xylophone pling bit banjo didgeridoo cow_bell snare hat basedrum iron_xylophone");
						return;
					}
					note = Math.max(0, Math.min(24, note));
					float pitch = (float) Math.pow(2.0, (note - 12) / 12.0);
					BlockPos p = c.getBlockPos();
					level.playSound(null, p, instrument.getSoundEvent().value(), SoundSource.RECORDS, 3.0F, pitch);
					level.sendParticles(ParticleTypes.NOTE, p.getX() + 0.5, p.getY() + 1.2, p.getZ() + 0.5, 0, note / 24.0, 0.0, 0.0, 1.0);
				}
				case "say", "broadcast" -> {
					Component msg = Component.literal("[" + c.label() + "] " + rest);
					for (ServerPlayer p : nearbyPlayers(c, level, 48)) p.sendSystemMessage(msg);
					if (script == null) c.print("Said: " + rest);
				}

				// ---- wireless ----
				case "listen" -> {
					if (rest.isEmpty()) c.print(c.channel() < 0 ? "Not listening. Use: listen <channel>" : "Listening on channel " + c.channel());
					else if (rest.equalsIgnoreCase("off")) {
						c.setChannel(level, -1);
						c.print("Stopped listening");
					} else {
						int ch = (int) Expr.num(rest);
						c.setChannel(level, ch);
						if (script == null) c.print("Listening on channel " + ch);
					}
				}
				case "send", "transmit" -> {
					if (parts.length < 3) {
						c.print("Usage: send <channel> <message>");
						return;
					}
					int ch = (int) Expr.num(parts[1]);
					String msg = line.substring(line.indexOf(parts[1], parts[0].length()) + parts[1].length()).trim();
					int n = Wireless.send(level, c.getBlockPos(), ch, c.label(), msg);
					if (script == null) c.print("Sent to " + n + " computer" + (n == 1 ? "" : "s") + " on channel " + ch);
				}
				case "inbox" -> {
					String m;
					int n = 0;
					while ((m = c.popMessage()) != null) {
						c.print("  " + m);
						n++;
					}
					if (n == 0) c.print("No new messages");
				}

				// ---- apps are opened on the client; if one gets here, explain ----
				case "edit", "notepad", "paint", "snake", "clock", "settings" -> c.print("Open " + cmd + " from the desktop or type it in the Terminal app");
				default -> c.print("Unknown command '" + cmd + "'. Type 'help'.");
			}
		} catch (Expr.ExprError e) {
			c.print("Error: " + e.getMessage());
			if (script != null) script.stop();
		}
	}

	private static @Nullable String findProgram(ComputerBlockEntity c, String name) {
		name = ComputerBlockEntity.cleanPath(name);
		for (String candidate : new String[] {name, name + ".bs", "programs/" + name, "programs/" + name + ".bs"}) {
			if (c.files().containsKey(candidate)) return candidate;
		}
		return null;
	}

	private static List<ServerPlayer> nearbyPlayers(ComputerBlockEntity c, ServerLevel level, double range) {
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(c.getBlockPos())) <= range * range) out.add(p);
		}
		return out;
	}

	private static String blockAt(ComputerBlockEntity c, ServerLevel level, String side) {
		Direction d = c.side(side);
		if (d == null) throw new Expr.ExprError("'" + side + "' isn't a side");
		return BuiltInRegistries.BLOCK.getKey(level.getBlockState(c.getBlockPos().relative(d)).getBlock()).getPath();
	}

	private static @Nullable NoteBlockInstrument instrument(String name) {
		for (NoteBlockInstrument i : NoteBlockInstrument.values()) {
			if (i.getSerializedName().equalsIgnoreCase(name) && i.isTunable()) return i;
		}
		return null;
	}
}
