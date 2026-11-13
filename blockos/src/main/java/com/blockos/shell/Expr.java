package com.blockos.shell;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tiny expression language used by BlockScript, `calc` and `${...}`.
 * Numbers, "strings", true/false, variables, + - * / % ^, comparisons, and/or/not, and function calls
 * like input(left) or random(6). Values are Double or String; booleans are 1/0.
 */
public final class Expr {
	public interface Functions {
		Object call(String name, List<Object> args);
	}

	public static class ExprError extends RuntimeException {
		public ExprError(String msg) {
			super(msg);
		}
	}

	private final String s;
	private int pos;
	private final Map<String, Object> vars;
	private final Functions fns;

	private Expr(String s, Map<String, Object> vars, Functions fns) {
		this.s = s;
		this.vars = vars;
		this.fns = fns;
	}

	public static Object eval(String text, Map<String, Object> vars, Functions fns) {
		Expr e = new Expr(text, vars, fns);
		Object v = e.orExpr();
		e.ws();
		if (e.pos < e.s.length()) throw new ExprError("Unexpected '" + e.s.substring(e.pos) + "'");
		return v;
	}

	// ---------------------------------------------------------------- helpers

	public static double num(Object o) {
		if (o instanceof Double d) return d;
		try {
			return Double.parseDouble(o.toString().trim());
		} catch (NumberFormatException e) {
			throw new ExprError("'" + o + "' is not a number");
		}
	}

	public static boolean truthy(Object o) {
		if (o instanceof Double d) return d != 0.0;
		String str = o.toString();
		return !str.isEmpty() && !str.equals("false") && !str.equals("off") && !str.equals("0");
	}

	public static String str(Object o) {
		if (o instanceof Double d) {
			if (d == Math.rint(d) && Math.abs(d) < 1e15) return Long.toString(d.longValue());
			String r = String.format("%.4f", d).replaceAll("0+$", "");
			return r.endsWith(".") ? r.substring(0, r.length() - 1) : r;
		}
		return o.toString();
	}

	private static Double bool(boolean b) {
		return b ? 1.0 : 0.0;
	}

	private void ws() {
		while (this.pos < this.s.length() && Character.isWhitespace(this.s.charAt(this.pos))) this.pos++;
	}

	private boolean eat(String tok) {
		this.ws();
		if (this.s.startsWith(tok, this.pos)) {
			// keywords must not be the start of a longer word
			if (Character.isLetter(tok.charAt(0))) {
				int end = this.pos + tok.length();
				if (end < this.s.length() && (Character.isLetterOrDigit(this.s.charAt(end)) || this.s.charAt(end) == '_')) return false;
			}
			this.pos += tok.length();
			return true;
		}
		return false;
	}

	// ---------------------------------------------------------------- grammar

	private Object orExpr() {
		Object l = this.andExpr();
		while (this.eat("or") || this.eat("||")) {
			Object r = this.andExpr();
			l = bool(truthy(l) || truthy(r));
		}
		return l;
	}

	private Object andExpr() {
		Object l = this.notExpr();
		while (this.eat("and") || this.eat("&&")) {
			Object r = this.notExpr();
			l = bool(truthy(l) && truthy(r));
		}
		return l;
	}

	private Object notExpr() {
		if (this.eat("not") || this.eat("!")) return bool(!truthy(this.notExpr()));
		return this.compare();
	}

	private Object compare() {
		Object l = this.sum();
		for (;;) {
			if (this.eat("==") || this.eat("is")) l = bool(eq(l, this.sum()));
			else if (this.eat("!=")) l = bool(!eq(l, this.sum()));
			else if (this.eat("<=")) l = bool(num(l) <= num(this.sum()));
			else if (this.eat(">=")) l = bool(num(l) >= num(this.sum()));
			else if (this.eat("<")) l = bool(num(l) < num(this.sum()));
			else if (this.eat(">")) l = bool(num(l) > num(this.sum()));
			else return l;
		}
	}

	private static boolean eq(Object a, Object b) {
		if (a instanceof Double x && b instanceof Double y) return x.doubleValue() == y.doubleValue();
		return str(a).equalsIgnoreCase(str(b));
	}

	private Object sum() {
		Object l = this.product();
		for (;;) {
			if (this.eat("+")) {
				Object r = this.product();
				l = (l instanceof Double a && r instanceof Double b) ? (Object) (a + b) : str(l) + str(r);
			} else if (this.eat("-")) {
				l = num(l) - num(this.product());
			} else {
				return l;
			}
		}
	}

	private Object product() {
		Object l = this.power();
		for (;;) {
			if (this.eat("*")) l = num(l) * num(this.power());
			else if (this.eat("/")) {
				double d = num(this.power());
				if (d == 0) throw new ExprError("Divide by zero");
				l = num(l) / d;
			} else if (this.eat("%")) l = num(l) % num(this.power());
			else return l;
		}
	}

	private Object power() {
		Object base = this.unary();
		if (this.eat("^")) return Math.pow(num(base), num(this.power()));
		return base;
	}

	private Object unary() {
		if (this.eat("-")) return -num(this.unary());
		if (this.eat("+")) return num(this.unary());
		return this.atom();
	}

	private Object atom() {
		this.ws();
		if (this.pos >= this.s.length()) throw new ExprError("Expression ended too early");
		char c = this.s.charAt(this.pos);
		if (c == '(') {
			this.pos++;
			Object v = this.orExpr();
			if (!this.eat(")")) throw new ExprError("Missing )");
			return v;
		}
		if (c == '"' || c == '\'') {
			int end = this.s.indexOf(c, this.pos + 1);
			if (end < 0) throw new ExprError("Missing closing quote");
			String v = this.s.substring(this.pos + 1, end);
			this.pos = end + 1;
			return v;
		}
		if (Character.isDigit(c) || c == '.') {
			int start = this.pos;
			while (this.pos < this.s.length() && (Character.isDigit(this.s.charAt(this.pos)) || this.s.charAt(this.pos) == '.')) this.pos++;
			return Double.parseDouble(this.s.substring(start, this.pos));
		}
		if (c == '$') this.pos++;
		int start = this.pos;
		while (this.pos < this.s.length() && (Character.isLetterOrDigit(this.s.charAt(this.pos)) || this.s.charAt(this.pos) == '_')) this.pos++;
		if (start == this.pos) throw new ExprError("Unexpected '" + c + "'");
		String name = this.s.substring(start, this.pos);
		if (this.eat("(")) {
			List<Object> args = new ArrayList<>();
			if (!this.eat(")")) {
				do {
					args.add(this.argument());
				} while (this.eat(","));
				if (!this.eat(")")) throw new ExprError("Missing ) after arguments to " + name);
			}
			return this.fns.call(name.toLowerCase(), args);
		}
		switch (name.toLowerCase()) {
			case "true", "on", "yes": return 1.0;
			case "false", "off", "no": return 0.0;
			case "pi": return Math.PI;
			default:
		}
		Object v = this.vars.get(name);
		if (v == null) throw new ExprError("Unknown variable '" + name + "' (set it first with: set " + name + " = ...)");
		return v;
	}

	/** Function arguments: a bare word like `left` is passed as a string; anything else is an expression. */
	private Object argument() {
		this.ws();
		int save = this.pos;
		int start = this.pos;
		while (this.pos < this.s.length() && Character.isLetter(this.s.charAt(this.pos))) this.pos++;
		String word = this.s.substring(start, this.pos);
		this.ws();
		boolean bare = !word.isEmpty() && (this.pos >= this.s.length() || this.s.charAt(this.pos) == ',' || this.s.charAt(this.pos) == ')')
			&& !this.vars.containsKey(word) && !word.equals("true") && !word.equals("false");
		if (bare) return word;
		this.pos = save;
		return this.orExpr();
	}
}
