# BlockOS Computers

A Fabric mod for Minecraft 26.3 that adds a placeable **computer**. Right-click it to boot **BlockOS**, a desktop with nine apps: Terminal, Files, Notepad, Paint, Snake, Calc, Clock, Messenger and Settings, plus five themes and three wallpapers.

Computers also affect the world. They output and read **redstone** on any side, sense players, time, weather and nearby blocks, play note sounds, chat to players, and message each other **wirelessly**. You program them in **BlockScript**, a small language whose programs keep running with the screen closed.

**Site and download:** https://xandertaylorx.github.io/minecraft-mods/mods/blockos/

## How it's built
- `block/ComputerBlockEntity`: files (64 KB), terminal, six redstone outputs, name, wireless channel and the running program. Everything is saved with the world.
- `shell/Shell`: every command. `shell/Expr` is the expression language. `shell/Script` compiles BlockScript to a flat step list and runs at most 200 steps per tick, so loops can't freeze the server.
- `shell/Wireless`: channel registry for computer-to-computer messages.
- `net/Net`: the screen sends commands and file saves; the block sends its state back.
- `client/screen/ComputerScreen`: the desktop. Each app is a class in `client/apps/`.

## Commands
```bash
./gradlew build               # build/libs/blockos-<version>.jar
./gradlew runClientGameTest   # real client: redstone, files, programs, wireless, every app + screenshots
```
Results are printed as `OS-TEST` lines; screenshots go to `build/run/clientGameTest/screenshots/`.
