package com.blockos.block;

import java.util.Map;

/** The files every new computer starts with: a readme and example programs to learn from. */
final class DefaultFiles {
	private DefaultFiles() {
	}

	static void install(Map<String, String> files) {
		files.put("readme.txt", String.join("\n",
			"Welcome to BlockOS!",
			"",
			"Click the apps on the desktop, or open Terminal and type 'help'.",
			"",
			"Programs are written in BlockScript and live in the programs/ folder.",
			"Run one with:   run blink",
			"Stop it with:   stop",
			"Edit one with:  edit programs/blink.bs",
			"",
			"Programs keep running after you close the screen.",
			"A file called startup.bs runs automatically when the computer turns on.",
			"",
			"Sides are seen from the front of the screen:",
			"front back left right top bottom"));

		files.put("programs/blink.bs", String.join("\n",
			"# Blinks redstone on the right side forever.",
			"# Put a redstone lamp next to the computer's right side and type: run blink",
			"loop",
			"  redstone right on",
			"  wait 0.5",
			"  redstone right off",
			"  wait 0.5",
			"end"));

		files.put("programs/autodoor.bs", String.join("\n",
			"# Opens an iron door on the front when a player walks up.",
			"loop",
			"  if players(4) > 0",
			"    redstone front on",
			"  else",
			"    redstone front off",
			"  end",
			"  wait 0.25",
			"end"));

		files.put("programs/nightlights.bs", String.join("\n",
			"# Turns lamps on the back side on at night and off in the day.",
			"loop",
			"  if night()",
			"    redstone back on",
			"  else",
			"    redstone back off",
			"  end",
			"  wait 5",
			"end"));

		files.put("programs/alarm.bs", String.join("\n",
			"# Pressure plate or tripwire on the left? Sound an alarm and warn other computers.",
			"loop",
			"  if input(left) > 0",
			"    say Intruder alert!",
			"    send 1 intruder at ${block(front)}",
			"    repeat 4",
			"      play bell 18",
			"      wait 0.2",
			"      play bell 12",
			"      wait 0.2",
			"    end",
			"    wait 3",
			"  end",
			"  wait 0.2",
			"end"));

		files.put("programs/song.bs", String.join("\n",
			"# Plays a little tune on the note sounds.",
			"set notes = \"0 2 4 5 7 9 11 12 11 9 7 5 4 2 0\"",
			"set i = 0",
			"repeat 15",
			"  play harp ${word(notes, i)}",
			"  wait 0.25",
			"  set i = i + 1",
			"end"));

		files.put("programs/counter.bs", String.join("\n",
			"# Counts redstone pulses on the left side.",
			"set count = 0",
			"set was = 0",
			"loop",
			"  set now = input(left) > 0",
			"  if now and not was",
			"    set count = count + 1",
			"    print \"Pulses: \" + count",
			"  end",
			"  set was = now",
			"  wait 0.1",
			"end"));

		files.put("programs/chat.bs", String.join("\n",
			"# Prints every message that arrives on channel 1.",
			"listen 1",
			"print \"Listening on channel 1...\"",
			"loop",
			"  set m = message()",
			"  if m != \"\"",
			"    say Message: $m",
			"  end",
			"  wait 0.5",
			"end"));

		files.put("notes/ideas.txt", "Things to build:\n- automatic farm\n- secret door\n- message board between bases\n");
	}
}
