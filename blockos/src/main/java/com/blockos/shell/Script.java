package com.blockos.shell;

import com.blockos.block.ComputerBlockEntity;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;

/**
 * BlockScript: a small line-based language. Compiled once into a flat list of steps with jumps,
 * then run a limited number of steps per tick so a runaway loop can never freeze the server.
 *
 * <pre>
 * # comment
 * set count = 0
 * loop                       # forever
 *   if input(left) > 0
 *     redstone right on
 *   else
 *     redstone right off
 *   end
 *   wait 0.5
 * end
 * </pre>
 * Also: while COND ... end, repeat N ... end, break, stop, print EXPR, and any terminal command
 * (with $var or ${expression} filled in).
 */
public final class Script {
	public static class ScriptError extends Exception {
		public ScriptError(String msg) {
			super(msg);
		}
	}

	private static final int STEPS_PER_TICK = 200;

	private enum Op { CMD, SET, PRINT, IF, ELSE, WHILE, END_WHILE, REPEAT, END_REPEAT, WAIT, BREAK, STOP, NOP }

	private record Step(Op op, String a, String b, int line, int[] jump) {}

	private final List<Step> steps;
	private final String name;
	private final Map<String, Object> vars = new HashMap<>();
	private final Map<Integer, Integer> repeatLeft = new HashMap<>();
	private int pc;
	private long wakeTick = -1;
	private boolean done;

	private Script(List<Step> steps, String name) {
		this.steps = steps;
		this.name = name;
	}

	public boolean isDone() {
		return this.done;
	}

	public Map<String, Object> vars() {
		return this.vars;
	}

	// ---------------------------------------------------------------- compiler

	public static Script compile(String src, String name) throws ScriptError {
		List<Step> out = new ArrayList<>();
		ArrayDeque<Integer> blocks = new ArrayDeque<>();      // index of the step that opened each block
		ArrayDeque<List<Integer>> breaks = new ArrayDeque<>(); // break statements per loop
		String[] lines = src.split("\n");
		for (int i = 0; i < lines.length; i++) {
			int lineNo = i + 1;
			String line = lines[i].trim();
			int hash = line.indexOf('#');
			if (hash >= 0 && !line.substring(0, hash).contains("\"")) line = line.substring(0, hash).trim();
			if (line.isEmpty()) continue;
			String word = line.split("\\s+", 2)[0].toLowerCase();
			String rest = line.length() > word.length() ? line.substring(word.length()).trim() : "";
			switch (word) {
				case "set", "let" -> {
					int eq = rest.indexOf('=');
					if (eq < 0) throw new ScriptError("line " + lineNo + ": use  set name = value");
					String var = rest.substring(0, eq).trim().replace("$", "");
					if (!var.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new ScriptError("line " + lineNo + ": bad variable name '" + var + "'");
					out.add(new Step(Op.SET, var, rest.substring(eq + 1).trim(), lineNo, new int[1]));
				}
				case "print", "say_screen" -> out.add(new Step(Op.PRINT, rest.isEmpty() ? "\"\"" : rest, "", lineNo, new int[1]));
				case "if" -> {
					blocks.push(out.size());
					out.add(new Step(Op.IF, need(rest, lineNo, "if"), "", lineNo, new int[1]));
				}
				case "else" -> {
					if (blocks.isEmpty() || out.get(blocks.peek()).op != Op.IF) throw new ScriptError("line " + lineNo + ": else without if");
					int ifIdx = blocks.pop();
					blocks.push(out.size());
					out.add(new Step(Op.ELSE, "", "", lineNo, new int[1]));
					out.get(ifIdx).jump[0] = out.size();          // false branch lands after else
				}
				case "while", "loop" -> {
					blocks.push(out.size());
					breaks.push(new ArrayList<>());
					out.add(new Step(Op.WHILE, word.equals("loop") ? "1" : need(rest, lineNo, "while"), "", lineNo, new int[1]));
				}
				case "repeat" -> {
					blocks.push(out.size());
					breaks.push(new ArrayList<>());
					out.add(new Step(Op.REPEAT, need(rest, lineNo, "repeat"), "", lineNo, new int[1]));
				}
				case "end" -> {
					if (blocks.isEmpty()) throw new ScriptError("line " + lineNo + ": 'end' without a block to close");
					int open = blocks.pop();
					Step o = out.get(open);
					switch (o.op) {
						case IF, ELSE -> {
							out.add(new Step(Op.NOP, "", "", lineNo, new int[1]));
							o.jump[0] = out.size();
						}
						case WHILE, REPEAT -> {
							out.add(new Step(o.op == Op.WHILE ? Op.END_WHILE : Op.END_REPEAT, "", "", lineNo, new int[] {open}));
							o.jump[0] = out.size();
							for (int b : breaks.pop()) out.get(b).jump[0] = out.size();
						}
						default -> throw new ScriptError("line " + lineNo + ": confusing 'end'");
					}
				}
				case "break" -> {
					if (breaks.isEmpty()) throw new ScriptError("line " + lineNo + ": break outside a loop");
					breaks.peek().add(out.size());
					out.add(new Step(Op.BREAK, "", "", lineNo, new int[1]));
				}
				case "wait", "sleep" -> out.add(new Step(Op.WAIT, rest.isEmpty() ? "1" : rest, "", lineNo, new int[1]));
				case "stop", "exit" -> out.add(new Step(Op.STOP, "", "", lineNo, new int[1]));
				default -> out.add(new Step(Op.CMD, line, "", lineNo, new int[1]));
			}
		}
		if (!blocks.isEmpty()) throw new ScriptError("missing 'end' for the block on line " + out.get(blocks.peek()).line);
		return new Script(out, name);
	}

	private static String need(String rest, int line, String what) throws ScriptError {
		if (rest.isEmpty()) throw new ScriptError("line " + line + ": '" + what + "' needs a condition or number");
		return rest;
	}

	// ---------------------------------------------------------------- runner

	public void tick(ComputerBlockEntity c, ServerLevel level) {
		if (this.done) return;
		if (this.wakeTick > level.getGameTime()) return;
		int budget = STEPS_PER_TICK;
		Expr.Functions fns = Shell.functions(c, level);
		while (budget-- > 0) {
			if (this.pc >= this.steps.size()) {
				this.done = true;
				return;
			}
			Step s = this.steps.get(this.pc);
			try {
				switch (s.op) {
					case CMD -> {
						this.pc++;
						Shell.execute(c, level, null, Shell.interpolate(s.a, this.vars, fns), this);
						if (this.done) return;
					}
					case SET -> {
						this.vars.put(s.a, Expr.eval(s.b, this.vars, fns));
						this.pc++;
					}
					case PRINT -> {
						c.print(Expr.str(Expr.eval(s.a, this.vars, fns)));
						this.pc++;
					}
					case IF -> this.pc = Expr.truthy(Expr.eval(s.a, this.vars, fns)) ? this.pc + 1 : s.jump[0];
					case ELSE, BREAK -> this.pc = s.jump[0];
					case WHILE -> this.pc = Expr.truthy(Expr.eval(s.a, this.vars, fns)) ? this.pc + 1 : s.jump[0];
					case END_WHILE -> {
						this.pc = s.jump[0];
						return;                      // one loop pass per tick at most keeps things gentle
					}
					case REPEAT -> {
						int left = this.repeatLeft.computeIfAbsent(this.pc, k -> (int) Expr.num(Expr.eval(s.a, this.vars, fns)));
						if (left <= 0) {
							this.repeatLeft.remove(this.pc);
							this.pc = s.jump[0];
						} else {
							this.repeatLeft.put(this.pc, left - 1);
							this.pc++;
						}
					}
					case END_REPEAT -> this.pc = s.jump[0];
					case WAIT -> {
						double secs = Math.max(0.05, Expr.num(Expr.eval(s.a, this.vars, fns)));
						this.wakeTick = level.getGameTime() + Math.round(secs * 20.0);
						this.pc++;
						return;
					}
					case STOP -> {
						this.done = true;
						return;
					}
					case NOP -> this.pc++;
				}
			} catch (Expr.ExprError e) {
				c.print("Error in " + this.name + " line " + s.line + ": " + e.getMessage());
				this.done = true;
				return;
			}
		}
	}

	public void stop() {
		this.done = true;
	}
}
