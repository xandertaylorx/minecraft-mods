# <img src="docs/icon.png" width="32" align="top"> Inventory Sorter

A small **sort icon** on every chest, barrel, ender chest, shulker box and inventory screen. Click it, or press **R**, and the contents sort themselves. Client-side only, so it works on any server.

- **Icon button**: the top one sorts the container, the one on the "Inventory" row sorts your backpack. In your own inventory it sits under the crafting grid.
- **Quick stack** (inventory screen only): the arrow-into-chest icon sends every main-inventory item that a chest or barrel within reach (about 5 blocks on a server, 10 in singleplayer or LAN) already contains to that chest, nearest first, like Terraria. The hotbar is never touched. In singleplayer or a LAN host world it is instant; on a multiplayer server it opens chests behind the scenes (screens hidden), opens the chests near you behind the scenes (screens hidden), checking any chest it has never seen and skipping ones it knows hold nothing of yours. Every chest it opens is remembered for the session.
- **R** sorts the open container (or your inventory if none is open). **Shift + R** always sorts your 27 main inventory slots. Hotbar and armor are never touched. Rebind in Controls.
- Partial stacks are merged, then items follow the creative inventory order (stone with stone bricks, tools with tools, food with food).
- Uses ordinary slot clicks, 3 per tick, so it won't get you kicked for spam.

Requires Fabric API, Minecraft 26.3. Build with `./gradlew build`.
