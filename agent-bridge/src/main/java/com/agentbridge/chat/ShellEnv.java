package com.agentbridge.chat;

import com.agentbridge.AgentBridge;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Finds command-line tools the way your terminal would. Games started from a launcher get a bare PATH
 * (no Homebrew, no ~/.local/bin, no nvm), so on macOS and Linux we ask your login shell for its PATH once.
 */
final class ShellEnv {
	private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
	private static volatile String path;

	private ShellEnv() {}

	/** The PATH your terminal uses, with common install folders added. */
	static synchronized String path() {
		if (path != null) return path;
		Set<String> dirs = new LinkedHashSet<>();
		if (!WINDOWS) {
			String shellPath = loginShellPath();
			if (shellPath != null) dirs.addAll(List.of(shellPath.split(File.pathSeparator)));
		}
		String current = System.getenv("PATH");
		if (current != null) dirs.addAll(List.of(current.split(File.pathSeparator)));
		String home = System.getProperty("user.home");
		if (WINDOWS) {
			String appData = System.getenv("APPDATA");
			if (appData != null) dirs.add(appData + "\\npm");
			dirs.add(home + "\\.local\\bin");
		} else {
			dirs.add(home + "/.local/bin");
			dirs.add(home + "/.claude/local");
			dirs.add(home + "/.npm-global/bin");
			dirs.add(home + "/.bun/bin");
			dirs.add("/opt/homebrew/bin");
			dirs.add("/usr/local/bin");
			dirs.add("/usr/bin");
			dirs.add("/bin");
		}
		dirs.removeIf(String::isBlank);
		path = String.join(File.pathSeparator, dirs);
		return path;
	}

	/** Full path to a command, or null if it isn't installed. */
	static String find(String command, String override) {
		if (override != null && !override.isBlank()) {
			return Files.isRegularFile(Path.of(override)) ? override : null;
		}
		List<String> names = new ArrayList<>();
		if (WINDOWS) {
			names.add(command + ".exe");
			names.add(command + ".cmd");
			names.add(command + ".bat");
		}
		names.add(command);
		for (String dir : path().split(File.pathSeparator)) {
			for (String name : names) {
				Path p = Path.of(dir, name);
				if (Files.isRegularFile(p) && (WINDOWS || Files.isExecutable(p))) return p.toString();
			}
		}
		return null;
	}

	/** Builds a process command line; .cmd and .bat files on Windows have to go through cmd.exe. */
	static List<String> command(String executable, List<String> args) {
		List<String> cmd = new ArrayList<>();
		String lower = executable.toLowerCase(Locale.ROOT);
		if (WINDOWS && (lower.endsWith(".cmd") || lower.endsWith(".bat"))) {
			cmd.add("cmd.exe");
			cmd.add("/c");
		}
		cmd.add(executable);
		cmd.addAll(args);
		return cmd;
	}

	private static String loginShellPath() {
		String shell = System.getenv("SHELL");
		if (shell == null || shell.isBlank()) shell = Files.exists(Path.of("/bin/zsh")) ? "/bin/zsh" : "/bin/bash";
		try {
			Process p = new ProcessBuilder(shell, "-ilc", "printf '__AB_PATH__%s__AB_END__' \"$PATH\"")
				.redirectErrorStream(true)
				.redirectInput(ProcessBuilder.Redirect.from(new File(WINDOWS ? "NUL" : "/dev/null")))
				.start();
			if (!p.waitFor(10, TimeUnit.SECONDS)) {
				p.destroyForcibly();
				return null;
			}
			String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			int a = out.lastIndexOf("__AB_PATH__");
			int b = out.lastIndexOf("__AB_END__");
			return a >= 0 && b > a ? out.substring(a + "__AB_PATH__".length(), b) : null;
		} catch (IOException e) {
			return null;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		} catch (Exception e) {
			AgentBridge.LOGGER.debug("Could not read login shell PATH", e);
			return null;
		}
	}
}
