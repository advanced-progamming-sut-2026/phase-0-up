# group-31
Paniz Hosseini - 404105745\
Amir Lari - 404102556\
MohammadParsa Ayati - 404105542

---

# Plants vs. Zombies 2

Two front ends over one game. The terminal build and the graphical build drive the **same** models,
systems and commands -- only the renderers differ -- so anything you fix in one is fixed in both.

## Requirements

* **JDK 21 or newer** (developed on 25). Nothing else: the Gradle wrapper is committed, so do not
  install Gradle separately.
* Java toolchain auto-download is **off** on purpose (see `gradle.properties`) -- the build compiles to
  Java 21 bytecode with whatever JDK you already have.

## Running the terminal build

```bash
gradlew run
```

Works straight after cloning. Read `GUIDE.md` for the commands.

## Running the graphical build

```bash
gradlew runGui
```

This one needs the art, which is **not in the repository**. Without it you get:

```
The PvZ asset folder was not found: <project>\pvz-assets
```

That message is the whole problem -- the game is fine, it just has nothing to draw.

### Getting the assets

`pvz-assets/` is about **510 MB** of extracted PopCap data (`ATLASES/`, `IMAGES/`, `RESOURCES.json`).
It is git-ignored deliberately: half a gigabyte of binary art would be in every clone, every branch and
every fetch forever, and GitHub rejects individual files over 100 MB.

So it is passed around out of band -- ask a teammate for the folder (USB, LAN, or whatever file sharing
you already use; note that international downloads are unreliable from here, which is also why
`gradle.properties` disables Gradle's JDK auto-download).

Once you have it, pick one:

**Either** drop the folder in the project root so the layout is:

```
PvZ2-AP-Project/
  pvz-assets/
    ATLASES/
    IMAGES/
    RESOURCES.json
```

**Or** leave it wherever it already lives and point the build at it. Put this line in your **personal**
`~/.gradle/gradle.properties` -- not the one in the repo, so nobody else inherits your path:

```properties
systemProp.pvz.assets=D:/somewhere/pvz-assets
```

Gradle forwards any `pvz.*` system property into the game, so this is picked up automatically and you
never type it again. For a one-off run, `gradlew runGui -Dpvz.assets=D:/somewhere/pvz-assets` does the
same thing.

## Save files

The game writes `users_database.json` at the end of every level. It holds accounts and progress, so
treat it as **yours**: do not commit changes to it, or every teammate's playthrough will collide with
everyone else's. The game creates it on first run if it is missing.

## Useful flags

All of these work on `runGui`:

| Flag | What it does |
| --- | --- |
| `-Dpvz.assets=<path>` | Where the art lives |
| `-Dpvz.smokeFrames=<n>` | Run exactly n frames, then exit -- how rendering changes get verified without a human watching |
| `-Dpvz.screenshot=<file>` | Save a PNG on the last frame |
| `-Dpvz.debugCounts=1` | Per-second census of what is actually on the board |
| `-Dpvz.dumpParts=<entity>` | Print an animation's clips, bounds and part names |
| `-Dpvz.forceDamage=<0-3>` | Pin every plant to one damage stage |
| `-Dpvz.uiDebug=1` | Draw Scene2D layout bounds |

## Checks

```bash
gradlew build
```

Compiles, runs Checkstyle, and runs the tests -- including the ArchUnit rules that keep LibGDX out of
the models and controllers. If those fail, the MVC separation has been broken somewhere.
