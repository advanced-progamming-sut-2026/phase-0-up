# PvZ2 — Play & Test Guide

A terminal (text-command) Plants vs. Zombies. You type one command per line and press **Enter**; the game prints the result. This guide covers building, running, every command by menu, all cheats, the five mini-games, and a copy‑paste smoke test.

---

## 1. Build & run

**Requirements:** a **JDK 21+** (the code uses `List.getFirst()`, a Java 21 API — Java 17 will not compile it). The build is **Gradle**; Gson is fetched automatically. The entry point is `Main` (in `src/Main.java`, default package).

> ⚠️ **Always run from the project root.** Data files (`data/…`) and the save file (`users_database.json`) are opened with **relative** paths. The `run` task pins the working directory for you.

### Option A — IntelliJ (easiest)
1. Open the project folder. IntelliJ detects `build.gradle` and imports it (it will offer to create the Gradle wrapper if it is missing — accept).
2. Run the **`run`** Gradle task, or run `Main` directly. Type commands in the Run console.

### Option B — Command line
```bash
cd /c/javaprogramming/PvZ2-AP-Project

./gradlew run           # build + play (stdin is wired up, so you can type commands)
./gradlew build         # compile + run tests
./gradlew test          # tests only
./gradlew checkstyleMain   # lint report -> build/reports/checkstyle/main.html
./gradlew fatJar        # one runnable jar with Gson inside
```
On Windows `cmd`/PowerShell use `gradlew` (no `./`).

After `fatJar`, still launch **from the project root** so the relative data paths resolve:
```bash
java -jar build/libs/PvZ2-AP-Project-1.0-SNAPSHOT-all.jar
```

> **Project layout is non-standard on purpose.** Sources stay in `src/` (not `src/main/java`) and tests
> live in `test/`, both declared explicitly in `build.gradle`. That avoided moving 388 files and handing
> everyone on the team a merge conflict.

---

## 2. Getting around (works in every menu)

| Command | Effect |
|---|---|
| `menu show current` | Print which menu you're in |
| `menu enter <name>` | Go to a menu |
| `menu exit` | Leave the current menu (back out) |

**Menu names:** `signup` `login` `main` `play` `profile` `settings` `collection` `shop` `greenhouse` `news` `travel-log` `leaderboard`.

On a **fresh start** (no saved login) you begin with no menu — start with `menu enter signup` or `menu enter login`. If a login was saved, the game auto‑logs you in to the **main** menu.

### Coordinates
Board is **9 columns × 5 rows**. Every location is `(x, y)`:
- `x` = column, **0–8**, left → right (the house is on the left, at x = 0; zombies enter from the right).
- `y` = row, **0–4**, top → bottom.

Plants sit on integer tiles; zombies move continuously (their x can be fractional).

---

## 3. Create an account & log in

**Register** (in the `signup` menu). Gender is `male` or `female`:
```
register -u <username> -p <password> <passwordConfirm> -n <nickname> -e <email> -g <male|female>
```
You are then shown 5 security questions — answer with:
```
pick question -q <1-5> -a <answer> -c <answerConfirm>
```
On success you're moved to the `login` menu.

- Username: letters, digits, `-`.
- Passwords must match; answer and its confirm must match.

**Log in** (in the `login` menu):
```
login -u <username> -p <password>
login -u <username> -p <password> -stay-logged-in
```
`-stay-logged-in` makes the game auto‑log you in next launch.

**Forgot password** (in the `login` menu):
```
forget password -u <username> -e <email>
answer -a <securityAnswer>
```

**Log out** (from `main`): `menu logout`

Example:
```
menu enter signup
register -u tester -p Pass123! Pass123! -n Testy -e testy@example.com -g male
pick question -q 1 -a fluffy -c fluffy
menu enter login
login -u tester -p Pass123!
```

---

## 4. Main‑menu sub‑menus

### Profile (`menu enter profile`)
```
menu profile show-info
menu profile change-username -u <newName>
menu profile change-nickname -u <newNickname>
menu profile change-email -e <newEmail>
menu profile change-password -p <newPass> -o <oldPass>
```

### Settings (`menu enter settings`)
```
menu settings change-difficulty -l <1-5>
```
Difficulty scales zombie/sun rates. **5 = maximum** (used by the "Win After Win" quest).

### Collection (`menu enter collection`)
```
menu collection show-plants
menu collection show-all-plants
menu collection show-zombies
menu collection show-all-zombies
menu collection show-zombie -z <zombieName>
menu collection upgrade-plant -p <plantName>
menu collection purchase-plant -p <plantName>
```
`-p`/`-z` accept names with spaces (e.g. `Cherry Bomb`).

### Shop (`menu enter shop`)
```
shop buy -i <itemId> -n <quantity>
shop buy -i <itemId> -n <quantity> -t <plantType>
```

### News (`menu enter news`)
```
menu news show-all
menu news show-unread
```
The main menu shows a red **[* NEW]** badge on the News line whenever you have unread news. News is
posted automatically when you unlock a new plant, meet a new zombie for the first time, or unlock a new
mini‑game level. Reading it clears the badge.

### Leaderboard (`menu enter leaderboard`)
```
leaderboard show
leaderboard sort -c <column> -o <asc|desc>
```
Ranks every registered player side by side. `<column>` is one of `stage`, `minigames`, `daily`,
`quests`, `score` — the stage they've reached (e.g. `Stage 2-3`), mini‑games completed, daily and
non‑daily quests completed, and their best **Meow Points** (from the Scoring Game — see §8). `-o` picks
ascending or descending, so you can sort on any column in either direction.

### 🏆 Scoring Game (`menu scoring-game`)
Playable straight from the main menu, or from the Play menu. Full rules in **§8** below.

### Greenhouse (`menu enter greenhouse`, or from Play menu — see below)
```
show greenhouse
plant pot at (<x>, <y>)
grow (<x>, <y>)
collect (<x>, <y>)
enter shop
```

---

## 5. Play an adventure level

Enter the play menu and manage the campaign:
```
menu enter play
menu coin-wallet          # show coins
menu gem-wallet           # show gems
menu cheat add <n> coin       # 💰 give yourself coins (great for testing)
menu cheat add <n> diamond    # 💎 give yourself gems
menu enter chapter -c <chapterNumber>
level -l <levelNumber>
```
`level -l <n>` puts you in the **plants menu** (seed selection) for that level.

Also reachable from the play menu:
```
menu greenhouse
menu travel-log       # quests + mini-games (Section 7)
menu leaderboard
```

### Seed selection (the plants menu)
```
show all plants           # everything in the catalogue
show available plants     # what you own / can pick this level
add plant -t <plantType>
remove plant -t <plantType>
boost plant -t <plantType>    # pre-charges the plant's Plant Food for its first placement
start game
```
`-t` here accepts names with spaces. **Your starter plants:** `Sunflower`, `Peashooter`, `Wall-nut`, `Potato Mine`, `Snow Pea`.

`start game` drops you into the live game.

---

## 6. In‑game commands

These work once the level is running.

### Sun & time
```
show sun amount
collect sun -l (<x>, <y>)          # collect sun a plant produced / that fell there
advance time -t <count> ticks      # ⏩ advance the simulation (1 tick = 1 in-game second)
```
> **Nothing moves until you advance time.** Plant, then `advance time -t 30 ticks` to watch waves, shots, and sun play out.

### Plants
```
plant plant -t <plantType> -l (<x>, <y>)
pluck plant -l (<x>, <y>)
feed plant -l (<x>, <y>)           # spend a Plant Food on the plant at (x, y)
```

### Inspecting the board
```
show map                           # full board: plants, zombies, terrain, waves, sun, mowers
show plants status                 # each plant's cost / cooldown (in Vasebreaker: the plants in hand)
show tile status -l (<x>, <y>)     # what's on one tile
zombies info                       # every live zombie: position, body HP, armour layers, timed effects
```

`zombies info` prints one block per zombie, e.g.:
```
ZombieArmor2:
    position: 7, 1
    health: 190
    armor:
        bucket: 1100
    effects:
        chilled: 3.2s
```
`health` is the **body** HP; armour layers are listed separately and are stripped top‑down before the body
takes damage. Effects show the time left (`frozen`, `chilled`, `buttered`).

### Map legend

| Symbol | Meaning | Where |
|---|---|---|
| `[.]` | empty, plantable ground | everywhere |
| `[x]` | not plantable | everywhere |
| `[P]` | a plant (or protector) | normal levels |
| `[Z]` | a zombie | normal levels |
| `[?]` | ordinary vase — could be anything | Vasebreaker |
| `[*]` | **plant vase** — always a seed packet | Vasebreaker |
| `[G]` | **Gargantuar vase** — always a Gargantuar | Vasebreaker |
| `[S]` | a dropped seed packet (grab it before it fades!) | Vasebreaker |
| `[Pe]` `[Su]` `[Wa]` `[Pu]` `[Ca]` … | plant type codes | Beghouled |
| `[##]` | a crater — nothing can ever go here again | Beghouled |
| `[Z!]` | a zombie standing on a tile | Beghouled |

Terrain tiles show their own letter (e.g. water, ice, graves).

### Cheats (for fast testing)
```
cheat add -n <count> suns                        # add sun
cheat add-plant-food                             # +1 Plant Food
cheat remove-cooldown                            # remove all plant recharge cooldowns
cheat spawn-zombie -t <zombieType> -l (<x>, <y>) # drop any zombie on any tile
release the nuke                                 # kill every zombie on the board
```
`<zombieType>` is a zombie alias — `ZombieDefault`, `ZombieArmor1` (cone), `ZombieArmor2` (bucket),
`ZombieGargantuar`, `ZombieImp`, … (matching is case‑insensitive). Use `menu enter collection` →
`show zombies` for the full list.

**Win** = clear all waves with no zombie reaching the house. **Lose** = a zombie breaches a row whose lawn mower is spent. Either way you return to the menu.

---

## 7. Mini‑games (Travel Log)

Open the Travel Log (from the **play** menu):
```
menu travel-log
travel log page <main|daily|epic|all|minigames>   # browse quests / list mini-games
travel log play <game> [-d <difficulty>]          # launch a mini-game
```
`<game>` is one of: `vasebreaker`, `bowling`, `izombie`, `beghouled`, `zombotany`. `-d` is optional (defaults to 1; higher = harder).

Each mini-game uses the standard in‑game commands (`advance time`, `show map`, …) **plus** its own:

### 🏺 Vasebreaker — `travel log play vasebreaker`
No sun, no seed picking, **no lawn mowers**. Break every vase without letting a zombie reach the house.
```
break vase -l (<x>, <y>)           # smash a vase (zombie / seed / empty)
collect seed -l (<x>, <y>)         # pick up a seed a broken vase dropped
plant plant -t <plant> -l (<x>, <y>)
show plants status                 # what's currently in your hand
```
**How the board is laid out.** Vases fill **columns 3–8 solid** — one on every tile. Columns 0–2 start
completely empty: that is your build space, and it guarantees a zombie always has lawn to cross.

**The three vase types** (see the map legend above):
- `[?]` ordinary — a gamble. Far more often a zombie than a seed packet, sometimes empty.
- `[*]` plant vase — **always** a seed packet. There are several per board; these are your reliable supply.
- `[G]` Gargantuar vase — **always** a Gargantuar. Exactly one per board. Save it for when you're ready.

**Plants come only from vases.** There is no seed selection and no sun at all — a collected plant is
free to place. Once you plant it, it leaves your hand. Vases never hand out sun producers, water plants
or lily pads, since none of those would be any use on this dry lawn.

> ⏳ **A dropped seed packet fades after ~10 seconds** (`[S]` on the map). Collect it and plant it fast, or
> it's destroyed. This is the real difficulty of the mode — you'll break far more vases than you can use.

### 🎳 Wall‑nut Bowling — `travel log play bowling`
No sun; bowl nuts from **behind the red line (columns 0–2)**. Nuts roll right into zombies.
```
bowl -t <bowling|explode|giant> -l (<x>, <y>)
show map                           # shows your nut supply and the wave counter
```
- `bowling` — hits a zombie then turns 45°, bounces 90° off top/bottom walls.
- `explode` — 3×3 blast on the first zombie.
- `giant` — crushes everything in a straight line.

**Your nut supply is limited.** A conveyor belt hands you a new nut every ~8 seconds, up to 6 held at
once. `show map` reports exactly what you're holding, so you always know what you can throw:
```
Wave 2/3  |  Nuts ready (4/6): bowling x2, explode x1, giant x1
```
Bowling a kind you don't have is refused — check the belt first.

**Zombies come in waves**, not one opening rush: 3 waves at difficulty 1, one more per difficulty tier,
and **each wave is bigger than the last**. Clearing the lawn early does *not* win the level — you win only
once the final wave has arrived and been wiped out.

### 🧟 I, Zombie — `travel log play izombie`
You play the zombies. Start with 150 sun (sun-maker zombies grow your income). Summon zombies **right of the red line (columns 5–8)** to eat the brains at each row's left end.
```
summon -t <ZombieDefault|ZombieImp|ZombieRa|ZombieExplorer|ZombieArmor2|...> -l (<x>, <y>)
```
(Use `show map` to see which zombies your roster offers and their costs; the buckethead **sun‑maker** can't be summoned.)

### 💎 Beghouled — `travel log play beghouled`
Match‑3 played on the lawn, while real zombies walk in. Swap two adjacent plants **only** if it makes a
line of 3+ of one type; an illegal swap is undone and costs you nothing.
```
swap -l (<x1>, <y1>) (<x2>, <y2>)
upgrade -t <plantType>
show map                           # the board, plus your match progress
```
The board shows a **two‑letter code per plant** so you can plan a swap — `[Pe]` Peashooter, `[Su]`
Sunflower, `[Wa]` Wall‑nut, `[Pu]` Puff‑shroom, `[Ca]` Cabbage‑pult, and after upgrades `[Re]` Repeater,
`[Ta]` Tall‑nut, `[Fu]` Fume‑shroom, `[Me]` Melon‑pult, `[MG]` Mega Gatling Pea, `[WM]` Winter Melon.

**Sun comes only from matches** (no sky sun):
| Match | Pays |
|---|---|
| 3 in a row | 50 sun |
| 4 in a row | 100 sun |
| 5 in a row | 150 sun |
| a **cascade** (a match formed by the refill after your move) | +50 on top |

Spend that sun on `upgrade -t <plantType>`, which upgrades **every** plant of that type on the board at
once. Chains: `Peashooter→Repeater→Mega Gatling Pea`, `Cabbage-pult→Melon-pult→Winter Melon`, plus
`Wall-nut→Tall-nut`, `Puff-shroom→Fume-shroom`.

**Craters.** When a zombie eats a plant it leaves a crater (`[##]`). Nothing can ever occupy that tile
again — not by refill, not by swapping — so letting zombies chew through the board permanently shrinks
your playing field. Plants fall *around* craters when a column collapses.

**Winning.** You need a target number of matches (5, +5 per difficulty tier — `show map` tracks it as
`Matches: 3/10`). Hitting the target instantly **wipes every zombie off the lawn** and you win. You lose
if a zombie reaches the house — there are no lawn mowers here.

> 🔄 If the board ever runs out of legal swaps, it reshuffles itself automatically into a fresh random
> layout, so you can never get stuck.

### 🌱 Zombotany — `travel log play zombotany`
A **normal level** (you pick plants and start it like any level) whose zombies have plant powers: a Peashooter zombie shoots your plants, a Wall‑nut zombie is very tanky, a Jalapeno zombie burns its row after ~10s, a Squash zombie crushes a plant on contact. Uses the ordinary seed‑selection + in‑game commands.

---

## 8. 🏆 Scoring Game — `menu scoring-game`

The bonus mode. It plays **exactly like an adventure level** — you pick plants, sun falls, waves come,
lawn mowers save you once — but you're not just trying to survive. You're playing for **Meow Points**,
and your best run goes on the leaderboard.

```
menu scoring-game          # from the main menu OR the play menu
```
It routes through normal seed selection, so pick your loadout and then `start`.

### Everyone gets the same lawn today

The zombies are generated from a seed derived from **today's date**, so every player in the world faces
the *same* waves, the *same* zombie types, in the *same* lanes on a given day — which is the only way
comparing scores means anything. At midnight the lawn changes for everybody. Replaying today won't
reroll it, so you can practise the same assault and try to beat your own score.

### The five ways to score

| # | Bonus | Worth | How to earn it |
|---|---|---|---|
| 1 | **Simultaneous Kill** | +50 | Kill 2+ zombies within the same second. Awarded once per burst — a 4‑zombie Cherry Bomb pays 50, not 150. |
| 2 | **Speed Kill** | +30 | Kill a zombie within 5 seconds of it walking on. Front‑load your damage. |
| 3 | **One‑Shot Kill** | +20 | An **explosive** plant (Cherry Bomb, Jalapeno…) killing a zombie **at full health**. Finishing off a wounded zombie doesn't count. |
| 4 | **Sun Hoarder** | +1 per 10 | Every 10 sun still unspent when the level ends. |
| 5 | **Flawless Defense** | +100 each | Every lawn mower still untriggered at the end. |

Rules 1–3 fire during play; 4–5 are settled from the final board.

### Reading the scorecard

When the level ends you get a breakdown of what actually paid:

```
===== MEOW POINTS =====
  Sun Hoarder          x9      +9
  Flawless Defense     x4    +400
  TOTAL                      409
Zombies destroyed: 37
```
Only rules that fired are listed. If the total beats your previous best it's saved to your profile and
shows up in the leaderboard's **Meow Points** column.

### Strategy notes

The rules deliberately pull against each other, which is the point — there's no single "correct" run:

- **Hoarding vs. spending.** Leftover sun scores (rule 4), but sun spent on plants is what keeps mowers
  intact (rule 5, worth far more per unit). A mower is 100; 10 sun is 1.
- **Explosives are a gamble.** A Cherry Bomb on a fresh crowd can trigger rules 1 *and* 3 at once. Drop
  it on a chewed‑up wave and you get neither.
- **Killing early beats killing safely.** Rule 2 rewards damage at the right edge of the lawn, which is
  the opposite of the usual "let them walk into your Wall‑nuts" instinct.

---

## 9. Copy‑paste smoke test

Paste these lines one block at a time (register once; on later runs just `login`).

```
menu enter signup
register -u tester -p Pass123! Pass123! -n Testy -e testy@example.com -g male
pick question -q 1 -a fluffy -c fluffy
menu enter login
login -u tester -p Pass123!
menu enter play
menu cheat add 5000 coin
menu enter chapter -c 1
level -l 1
add plant -t Sunflower
add plant -t Peashooter
add plant -t Wall-nut
start game
cheat add -n 500 suns
plant plant -t Sunflower -l (0, 2)
plant plant -t Peashooter -l (1, 2)
plant plant -t Wall-nut -l (5, 2)
show map
advance time -t 40 ticks
show map
release the nuke
advance time -t 5 ticks
```

Then try a mini-game:
```
menu exit
menu travel-log
travel log play bowling
bowl -t giant -l (0, 2)
advance time -t 20 ticks
show map
```

---

## 10. Testing tips & gotchas

- **Reset all accounts/progress:** stop the game and delete (or rename) `users_database.json` in the project root — the next launch starts empty. Progress (coins, gems, unlocked plants, completed quests, chapter/level, greenhouse) is saved here; live game state is **not** persisted.
- **Speed things up:** `menu cheat add … coin/diamond` (menus) and `cheat add -n … suns` / `cheat remove-cooldown` / `release the nuke` (in‑game).
- **Plant‑name argument quirk:** in‑game `plant plant -t`, `bowl -t`, and `summon -t` read the type as a **single token** (no spaces). Use single‑word or hyphenated names there (`Peashooter`, `Wall-nut`, `Repeater`). The seed‑selection `add plant -t`, collection `-p`, and Beghouled `upgrade -t` **do** accept spaces (`Cherry Bomb`).
- **Plant Food:** `cheat add-plant-food` grants one instantly; glowing zombies also drop it. Spend it with `feed plant -l (x, y)`.
- **Names are case‑sensitive** where they matter — match the catalogue casing (`Wall-nut`, not `wall-nut`) for plant commands.
- **Script a run:** pipe a command file into the game for repeatable tests. Build once with `./gradlew fatJar`, then `java -jar build/libs/PvZ2-AP-Project-1.0-SNAPSHOT-all.jar < test-commands.txt` (from the project root).
- Use `show map` liberally — it's your main window into what the simulation is doing.

---

## 11. Command quick reference

| Context | Command |
|---|---|
| Any menu | `menu show current` · `menu enter <name>` · `menu exit` |
| Signup | `register -u .. -p .. .. -n .. -e .. -g <male\|female>` · `pick question -q <1-5> -a .. -c ..` |
| Login | `login -u .. -p .. [-stay-logged-in]` · `forget password -u .. -e ..` · `answer -a ..` |
| Main | `menu logout` · `menu scoring-game` |
| Settings | `menu settings change-difficulty -l <1-5>` |
| Profile | `menu profile show-info` · `change-username/-nickname/-email/-password` |
| Collection | `show-plants` · `show-zombies` · `upgrade-plant -p ..` · `purchase-plant -p ..` |
| Shop | `shop buy -i <id> -n <qty> [-t <type>]` |
| Greenhouse | `show greenhouse` · `plant pot at (x,y)` · `grow (x,y)` · `collect (x,y)` |
| Play | `menu coin-wallet` · `menu gem-wallet` · `menu cheat add <n> <coin\|diamond>` · `menu enter chapter -c <n>` · `level -l <n>` · `menu travel-log` · `menu leaderboard` |
| Seeds | `show all/available plants` · `add/remove/boost plant -t <type>` · `start game` |
| In‑game | `collect sun -l (x,y)` · `show sun amount` · `advance time -t <n> ticks` · `plant plant -t <t> -l (x,y)` · `pluck plant -l (x,y)` · `feed plant -l (x,y)` · `show map` · `show plants status` · `show tile status -l (x,y)` |
| In‑game cheats | `cheat add -n <n> suns` · `cheat add-plant-food` · `cheat remove-cooldown` · `cheat spawn-zombie -t <alias> -l (x,y)` · `release the nuke` · `zombies info` |
| Scoring Game | `menu scoring-game` (main **or** play menu) |
| Travel Log | `travel log page <main\|daily\|epic\|all\|minigames>` · `travel log play <game> [-d <n>]` |
| Vasebreaker | `break vase -l (x,y)` · `collect seed -l (x,y)` |
| Bowling | `bowl -t <bowling\|explode\|giant> -l (x,y)` |
| I, Zombie | `summon -t <zombieAlias> -l (x,y)` |
| Beghouled | `swap -l (x1,y1) (x2,y2)` · `upgrade -t <plant>` |
