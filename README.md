# Minecraft Mods

A collection of Minecraft mods. Each top-level folder is a separate mod with its own Gradle build, so you can build or change one without touching the others.

| Mod | What it adds | Loader | Minecraft |
| --- | --- | --- | --- |
| [**Hero Arsenal**](hero-arsenal/) | Superhero gear: a lightning hammer, a ricochet shield, a blaster gauntlet and web shooters | Fabric | 26.3 |

---

## <img src="hero-arsenal/docs/icon.png" width="32" align="top"> Hero Arsenal

Four superhero weapons. Each one has a main ability on **right-click** and a second one on **sneak + right-click**. You'll find them all in the **Combat** creative tab.

![Mjolnir calling lightning on a group of mobs](hero-arsenal/docs/mjolnir-lightning.png)

### ⚡ Mjolnir
| Do this | What happens |
| --- | --- |
| Hold right-click, then let go | You wind it up over your head while sparks build up, then throw it. It tumbles through the air, calls lightning on whatever it hits, then flies back into your hand. A full 1-second charge throws it harder. |
| Sneak + right-click | Calls lightning wherever you're looking, up to 64 blocks away. |
| Melee | 12 damage. Critical hits always call lightning, and normal hits do 20% of the time. |

Your own lightning never hurts you. It does hit everything else nearby: it sets mobs on fire, charges creepers and turns pigs into zombie piglins. It never sets blocks on fire.

### 🛡️ Star Shield
| Do this | What happens |
| --- | --- |
| Hold right-click | Blocks attacks like a normal shield. It never breaks. |
| Sneak + right-click | Throws it. It bounces from enemy to enemy and off walls (up to 4 bounces), then comes back. |

<p>
  <img src="hero-arsenal/docs/shield-held.png" width="49%" alt="Holding the Star Shield">
  <img src="hero-arsenal/docs/shield-blocking.png" width="49%" alt="Blocking with the Star Shield">
</p>

### 🔥 Pulse Gauntlet
| Do this | What happens |
| --- | --- |
| Right-click | Fires a 40-block energy beam. It does 8 damage, knocks mobs back and sets them on fire. |
| Sneak + right-click | Thrusters launch you where you're looking. Chain them to fly. |

While it's in your hand, you take no fall damage.

### 🕸️ Web Shooter
| Do this | What happens |
| --- | --- |
| Right-click a mob | It gets stuck in webs (heavy slowness and weakness for 5 seconds). |
| Right-click a block | Pulls you toward that block, so you can swing around. |

While it's in your hand, you take no fall damage.

<p>
  <img src="hero-arsenal/docs/gauntlet-blast.png" width="49%" alt="Pulse Gauntlet blast">
  <img src="hero-arsenal/docs/web-hit.png" width="49%" alt="Web Shooter hitting a mob">
</p>

### Install
1. Use a **Fabric** instance for Minecraft **26.3** (in Prism Launcher, for example) with **Fabric API** installed.
2. Download [`hero-arsenal-1.0.0.jar`](https://github.com/xandertaylorx/minecraft-mods/releases/latest) and put it in the instance's `mods` folder.

Website: **https://xandertaylorx.github.io/minecraft-mods/**

---

## Building

Requires **Java 25**. Each mod comes with its own Gradle wrapper, so you don't need to install Gradle.

```bash
cd hero-arsenal
./gradlew build
```

The jar ends up in `<mod>/build/libs/`.

### Automated in-game test

Hero Arsenal has a test that launches a real Minecraft client, creates a world and uses every item on test mobs. It checks that each item works and saves screenshots along the way:

```bash
cd hero-arsenal
./gradlew runClientGameTest
```

Results are printed as `HA-TEST PASS/FAIL` lines, and screenshots go to `build/run/clientGameTest/screenshots/`. The screenshots in this README come from that test.

## Website

[`web/`](web/) holds the mods' advertising site, a hand-coded throwback to a 2009 fan site (dirt background, beveled boxes, marquee, blinking NEW! tags). It's plain HTML and CSS with no build step, and it deploys automatically to [GitHub Pages](https://xandertaylorx.github.io/minecraft-mods/) whenever `web/` changes on `main`. To preview it locally:

```bash
python3 -m http.server 8099 --directory web
```

Then open http://localhost:8099.

## Adding a new mod

Create a new top-level folder with its own Fabric project. Copying `hero-arsenal/` and renaming the mod ID, package and assets is the quickest start. Then add a row to the table at the top.
