# Mac FPS Boost

A tiny Fabric mod for Minecraft 26.3. On macOS, it stops the game from rendering at full Retina resolution. Minecraft creates its window with `SDL_WINDOW_HIGH_PIXEL_DENSITY`, which on a 2× display means 4× the pixels. This mod removes that one flag on macOS ([WindowMixin.java](src/main/java/com/macfpsboost/mixin/WindowMixin.java)). On Windows and Linux it does nothing.

**Download:** https://xandertaylorx.github.io/minecraft-mods/mods/mac-fps-boost/

To switch it off without removing it, add `-Dmacfpsboost.disable=true` to the JVM arguments.

`./gradlew runClientGameTest` checks the window's pixel density. It should be 2.0 with the mod disabled on a Retina screen, and 1.0 with it on.
