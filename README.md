# Plants vs. Zombies 2

A Java reimplementation of PvZ2 with **two front ends over one game**: a terminal build and a LibGDX
graphical build. Both drive the same models, systems and commands -- only the renderers differ -- so a
rule fixed in one is fixed in both.

Sharif University of Technology -- Advanced Programming, group 31.

| | |
| --- | --- |
| Paniz Hosseini | 404105745 |
| Amir Lari | 404102556 |
| MohammadParsa Ayati | 404105542 |

---

## Quick start

Commands are written for **PowerShell**, which needs the `.\` prefix. In cmd.exe or Git Bash, plain
`gradlew` works too.

```bash
.\gradlew run
```

That is the terminal build, and it works straight after cloning. See `GUIDE.md` for the command list.

```bash
.\gradlew runGui
```

The graphical build. This one needs the art, which is **not in the repository** -- see
[Assets](#assets) below. Without it you get a message telling you exactly that; nothing else is
missing.

---

## Requirements

* **JDK 21 or newer** (developed on 25).
* Nothing else. The Gradle wrapper is committed -- do **not** install Gradle separately.

Java toolchain auto-download is deliberately **off** (see `gradle.properties`): the build compiles to
Java 21 bytecode with whatever JDK you already have. A toolchain request used to hang the build
forever on a half-finished download, which showed up as IntelliJ's Run button silently doing nothing.

---

## Assets

`pvz-assets/` is about **510 MB** of extracted PopCap data -- `ATLASES/` (780 atlas pages), `IMAGES/`
(1458 `.PAM` skeletal animations) and `RESOURCES.json` (the manifest tying image ids to atlas
regions).

It is git-ignored on purpose. Half a gigabyte of binary art would sit in every clone, every branch and
every fetch forever, and GitHub rejects individual files over 100 MB. So it is passed around out of
band -- ask a teammate for the folder.

Once you have it, pick one:

**Either** drop it in the project root:

```
PvZ2-AP-Project/
  pvz-assets/
    ATLASES/
    IMAGES/
    RESOURCES.json
```

**Or** leave it wherever it lives and point the build at it. Put this in your **personal**
`~/.gradle/gradle.properties`, not the one in the repo, so nobody inherits your path:

```properties
systemProp.pvz.assets=D:/somewhere/pvz-assets
```

Gradle forwards any `pvz.*` system property into the game, so this is picked up automatically and you
never type it again. For one run only: `.\gradlew runGui -Dpvz.assets=D:/somewhere/pvz-assets`.

---

## Controls

| Input | Action |
| --- | --- |
| `1`--`9` | Arm the seed packet in that slot |
| Click a seed card | Arm that packet (click again to put it back) |
| Left click a tile | Plant / shovel / feed, depending on what you are holding |
| Left click a sun | Collect it -- works while it is still falling |
| Right click / `Esc` | Drop whatever the cursor is holding |
| `S` | Shovel |
| `F` | Plant food |
| `P` / `Space` | Pause (freezes the simulation **and** every animation) |
| `C` | Cheat panel |

The cursor carries what you are holding -- a seed shows that plant's own idle animation at the size it
will be once planted, so the preview and the result are the same art.

---

## How it is put together

```
src/
  models/       game state and rules. No LibGDX, no controllers. Plain Java.
  controllers/  systems (combat, waves, sun, quests) and the 57 Commands.
  views/
    renderers/  terminal output
    gdx/        the graphical build
      core/     Assets, Toasts, PvZGame, DebugFlags
      render/   board renderers (plants, zombies, projectiles, effects)
      ui/       Scene2D HUD (seed bank, wave meter, cheat panel)
      input/    mouse and keyboard, tool state
      screens/  the game screen and the animation preview harness
      bridge/   the seams between the model loop and the render loop
      sprite/   EntitySprite abstraction over libPVZ
      map/      LawnGeometry: model coordinates <-> screen pixels
data/           levels, plants, zombies and quests as JSON
test/           ArchUnit boundary rules and unit tests
```

**The rule that matters:** models and controllers must not import LibGDX, and models must not import
views. This is enforced by `MvcBoundaryTest`, which fails the build if it is broken -- including a
vacuity check so the test cannot silently pass by scanning nothing.

**The GUI does not reimplement any rule.** A mouse click is turned into the same command string the
terminal accepts and posted through `GameEngine.submitInGameCommand`. Clicking a tile with a seed
armed sends `plant plant -t Sunflower -l (3, 2)`. Every cost check, cooldown, occupied-tile rule and
quest tally therefore runs once and is written once. `CommandBridgeTest` holds the synthesised strings
against the engine's own patterns, because a malformed command matches nothing and fails *silently*.

The model ticks at 10 Hz while rendering runs at 60 fps; `EntityInterpolator` fills in between ticks so
motion is smooth. Animation is PopCap `.PAM` skeletal data played by libPVZ behind our own
`EntitySprite` interface.

---

## Development

```bash
.\gradlew build
```

Compiles, runs Checkstyle and runs the tests. If the ArchUnit rules fail, the MVC separation has been
broken somewhere.

| Task | Does |
| --- | --- |
| `.\gradlew run` | Terminal build |
| `.\gradlew runGui` | Graphical build |
| `.\gradlew build` | Compile + Checkstyle + tests |
| `.\gradlew fatJar` | Self-contained runnable jar |

Tests live in `test/`: `MvcBoundaryTest` (layer rules), `CommandBridgeTest` (click-to-command strings),
`MeowPointManagerTest` (scoring).

### Debug flags

All work on `runGui`. `build.gradle` forwards any `-Dpvz.*` flag into the forked JVM.

**Verifying a change without watching the window**

| Flag | Effect |
| --- | --- |
| `-Dpvz.smokeFrames=<n>` | Run exactly n frames at a fixed timestep, then exit |
| `-Dpvz.screenshot=<file>` | Save a PNG on the last frame |
| `-Dpvz.inputCheck=1` | Round-trip all 45 tiles through project/unproject, then plant, shovel and collect by simulated click |

The first two together are how a rendering change gets checked reproducibly. The fixed timestep means
two runs of the same frame count produce identical pixels.

**Finding out what the art actually contains**

| Flag | Effect |
| --- | --- |
| `-Dpvz.dumpParts=<Entity>` | Print an animation's clips, durations, bounds and **part names** |
| `-Dpvz.probeRegions=ID1,ID2` | Report whether each `RESOURCES.json` image id resolves, and its size |
| `-Dpvz.forceDamage=<0-3>` | Pin every plant to one damage stage |

Worth knowing: an entity's states are often **parts, not clips**. Wall-nut's `damage` / `damage2` /
`damage3` clips all draw a pristine shell -- the cracks are parts (`wallnut_body_front_damage_01`)
switched on with a visibility map, the same mechanism that puts a cone on a zombie. Always dump the
parts before concluding something is missing.

Assets live in two trees, `768/FULL/` **and** `768/INITIAL/`. Searching only one will convince you
that effects which do exist are absent.

**Everything else**

| Flag | Effect |
| --- | --- |
| `-Dpvz.debugCounts=1` | Per-second census of what is on the board |
| `-Dpvz.pause=1` | Start paused |
| `-Dpvz.grid=1` | Draw the lawn grid |
| `-Dpvz.lawn=x,y,w,h` | Re-tune lawn geometry without recompiling |
| `-Dpvz.uiDebug=1` | Draw Scene2D layout bounds |
| `-Dpvz.glProfile=1` | Log draw calls and texture bindings |
| `-Dpvz.screen=sprites` | Open the animation preview harness |
| `-Dpvz.showcase=1` | Set the board up with every hard-to-trigger effect on screen at once |

`showcase` is the one to reach for when checking visuals by eye: it plants a fire shooter, a Snow Pea
it then feeds, and a fresh Potato Mine; spawns zombies in every lane; lays out four suns of different
values side by side (including the radioactive one); arms a seed so the ghost cursor shows; and opens
the cheat panel. Everything it does is a command string the prompt accepts.

### Save files

The game writes `users_database.json` at the end of every level. It holds accounts and progress, so
treat it as **yours** -- do not commit changes to it, or every teammate's playthrough will collide. The
game recreates it on first run if missing.

---

## Troubleshooting

**"The PvZ asset folder was not found"** -- see [Assets](#assets). The build and the terminal game are
unaffected.

**Something is invisible but clearly working** -- check `-Dpvz.debugCounts=1` for whether it is on the
board at all. Half the bugs found in this project were the renderer drawing nothing; the other half
were the entity genuinely not existing.

**A click does nothing** -- a command that matches no pattern is ignored without an error. Watch the
toast: a rejected command reports its reason through the same renderer as everything else.
