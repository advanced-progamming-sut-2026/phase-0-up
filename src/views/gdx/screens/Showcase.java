package views.gdx.screens;

import controllers.engine.GameEngine;
import models.entities.collectibles.Sun;
import models.entities.collectibles.SunType;
import models.game.GameSession;
import views.gdx.input.ToolState;

// A board set up so every effect that is hard to trigger by accident is on screen at once.
//
// Run it with:  gradlew runGui -Dpvz.showcase=1
//
// This exists because a handful of things could only be confirmed by looking at them, and none of them
// happen in an ordinary opening minute: fire shots need a Fire Peashooter, the radioactive sun almost
// never rolls, sun sizes only differ if several values are on screen together, and a mine's unarmed
// pose lasts a few seconds once in a level.
//
// It changes nothing about the game -- every action here is a command string the prompt accepts, sent
// through the same door as a mouse click.
final class Showcase {

    private final GameEngine engine;
    private final GameSession session;

    Showcase(GameEngine engine, GameSession session) {
        this.engine = engine;
        this.session = session;
    }

    // Called once, a few frames in, so Scene2D has laid out and the board is ready.
    void run(ToolState tools) {
        engine.submitInGameCommand("cheat add -n 9999 suns");
        engine.submitInGameCommand("cheat remove-cooldown");

        plantBoard();
        plantSunShrooms();
        spawnZombies();
        makeSuns();
        throwImp();
        snareOne();

        // Arm a seed so the ghost cursor is visible the moment you move the mouse over the lawn.
        tools.selectSeed("Wall-nut");
    }

    // Row 0: fire. Row 1: ice, with the freeze fed in. Row 2: a fresh mine, still burying itself.
    private void plantBoard() {
        // Fire Peashooter is not in this level's seed bank, and plant() refuses anything that is not.
        // Handing the session a packet for it is the only way to get a fire shot on screen -- there is
        // no cheat command that adds a seed.
        if (session.getSelectedSeed("Fire Peashooter") == null) {
            session.addSeed(new models.game.SeedPacket("Fire Peashooter", 5));
        }
        boolean fire = engine.submitInGameCommand("plant plant -t Fire Peashooter -l (3, 0)");
        com.badlogic.gdx.Gdx.app.log("Showcase", "Fire Peashooter placed at (3, 0): "
                + (session.getMap().getRow(0).cellAt(1).hasPlant()
                        ? session.getMap().getRow(0).cellAt(1).getCurrentPlant().getName()
                        : "NOTHING (command accepted=" + fire + ", see the toast for why)"));
        // Rotobaga: the only shot in the game with its own flight animation, muzzle flash AND hit
        // splat, so it is the one that proves all three at once.
        if (session.getSelectedSeed("Rotobaga") == null) {
            session.addSeed(new models.game.SeedPacket("Rotobaga", 5));
        }
        // Middle lane on purpose: it fires up-left, up-right, down-left and down-right, and from the
        // top row half of those leave the board on the first tick.
        engine.submitInGameCommand("plant plant -t Rotobaga -l (2, 2)");
        engine.submitInGameCommand("plant plant -t Snow Pea -l (1, 1)");
        engine.submitInGameCommand("plant plant -t Potato Mine -l (3, 2)");
        engine.submitInGameCommand("plant plant -t Peashooter -l (1, 3)");
        engine.submitInGameCommand("plant plant -t Wall-nut -l (5, 3)");
        plantStrikers();
    }

    // The three plants that hit something with NOTHING in flight, plus the one whose throw has two
    // forms. None of their effects can be seen in an ordinary level: Caulipower and Electric Blueberry
    // pick a zombie anywhere on the board, and Kernel-pult's butter is a one-in-four roll.
    private void plantStrikers() {
        for (String name : new String[] {"Caulipower", "Electric Blueberry", "Kernel-pult"}) {
            if (session.getSelectedSeed(name) == null) {
                session.addSeed(new models.game.SeedPacket(name, 5));
            }
        }
        engine.submitInGameCommand("plant plant -t Caulipower -l (1, 2)");
        engine.submitInGameCommand("plant plant -t Electric Blueberry -l (2, 3)");
        engine.submitInGameCommand("plant plant -t Kernel-pult -l (2, 1)");
        plantShotVariety();
    }

    // One of each SHOT that has its own art, side by side.
    //
    // Every one of these was a green pea until the projectile art was keyed on the plant's chosen
    // ProjectileType rather than on its element -- a cabbage, a melon, a thorn and a star are all
    // NEUTRAL, so element alone could never tell them apart. Standing them in adjacent lanes turns
    // "does each plant throw its own thing" into a single look, the same way the Sun-shrooms do for
    // staged art.
    private void plantShotVariety() {
        for (String name : new String[] {"Cabbage-pult", "Melon-pult", "Cactus", "Starfruit"}) {
            if (session.getSelectedSeed(name) == null) {
                session.addSeed(new models.game.SeedPacket(name, 5));
            }
        }
        engine.submitInGameCommand("plant plant -t Cabbage-pult -l (2, 0)");
        engine.submitInGameCommand("plant plant -t Melon-pult -l (0, 1)");
        engine.submitInGameCommand("plant plant -t Cactus -l (0, 2)");
        engine.submitInGameCommand("plant plant -t Starfruit -l (0, 3)");

        // Jalapeno burns its whole lane and is gone in a second, so an ordinary run never shows it. Row 4
        // is the Sun-shroom row and has no zombies in it, so nothing is destroyed by putting it here.
        if (session.getSelectedSeed("Jalapeno") == null) {
            session.addSeed(new models.game.SeedPacket("Jalapeno", 5));
        }
        engine.submitInGameCommand("plant plant -t Jalapeno -l (4, 4)");
    }

    // Three Sun-shrooms in the back row, one per growth stage.
    //
    // Sun-shroom's art is cut one clip set per stage (see PlantStages) and the stages are 24 and 72
    // seconds apart, so an ordinary run shows exactly one of the three sizes and the transition between
    // them almost never. Standing all three side by side turns "does the staged art work" into one look.
    //
    // Columns 6-8 of row 4: the sun demo has 1, 3 and 5, and no zombie is spawned in row 4, so nothing
    // eats them before they have grown.
    private void plantSunShrooms() {
        if (session.getSelectedSeed("Sun-shroom") == null) {
            session.addSeed(new models.game.SeedPacket("Sun-shroom", 5));
        }
        for (int col = 6; col <= 8; col++) {
            engine.submitInGameCommand("plant plant -t Sun-shroom -l (" + col + ", 4)");
        }
        stageSunShrooms();
    }

    // Pushes each one to a different stage, through the model's own API rather than by reaching into
    // its fields: the second has its thresholds shortened so it grows within the first second (and so
    // the growth transition is catchable in a screenshot), and the third is jumped straight to the top
    // exactly as InstantGrowing plant food does it.
    private void stageSunShrooms() {
        int found = 0;
        for (models.map.Cell cell : session.getMap().getRow(4).getCells()) {
            models.entities.plants.Plant plant = cell.getCurrentPlant();
            if (plant == null || !"Sun-shroom".equalsIgnoreCase(plant.getName())) {
                continue;
            }
            found++;
            for (models.entities.plants.abilities.PlantAbility ability : plant.getAbilities()) {
                if (!(ability instanceof models.entities.plants.abilities.ProduceSunAbility sun)) {
                    continue;
                }
                if (found == 2) {
                    sun.reduceStageUpTicks(235);   // stage 2 arrives at tick 5, half a second in
                } else if (found == 3) {
                    sun.growToMaxStage();
                }
            }
        }
        com.badlogic.gdx.Gdx.app.log("Showcase", found + " Sun-shrooms planted in row 4"
                + " (stages 1, 2 and 3 left to right)");
    }

    private void spawnZombies() {
        // Something for every shooter to hit, and something for the freeze to land on.
        for (int row = 0; row <= 3; row++) {
            engine.submitInGameCommand("cheat spawn-zombie -t ZombieDefault -l (8, " + row + ")");
            engine.submitInGameCommand("cheat spawn-zombie -t ZombieDefault -l (7, " + row + ")");
        }
        engine.submitInGameCommand("cheat spawn-zombie -t ZombieDefault -l (4, 2)");
        // One almost at the house, for the danger glow (T8.6). Column 1 in row 2 rather than a lane a
        // shooter can reach: plants fire to the RIGHT, so a zombie behind them is not shot at and stays
        // put long enough to be looked at. It is also the only warning on this board that is a property
        // of a POSITION rather than of an event, so nothing else would raise it.
        engine.submitInGameCommand("cheat spawn-zombie -t ZombieDefault -l (1, 2)");
        // A Conehead and a Buckethead for the dismemberment work (T8.5). Spawned here and shot later,
        // in shedArmour(), because the effect is triggered by the armour VANISHING between two frames.
        engine.submitInGameCommand("cheat spawn-zombie -t ZombieArmor1 -l (6, 2)");
        engine.submitInGameCommand("cheat spawn-zombie -t ZombieArmor2 -l (5, 2)");
        hypnotiseOne();
    }

    // Breaks the armour off the two in row 2 and takes their bodies past half, so the fly-off and the
    // bone arm are both on screen.
    //
    // Unreachable otherwise in any run short enough to screenshot: a Buckethead is 1100 points of bucket
    // over 190 of body, which is most of a minute of Peashooter fire, and the fly-off itself lasts under
    // a second. Damage goes through applyDamage rather than into the HP fields, so the layers peel in
    // the real order and the renderer sees exactly what a shot-off bucket looks like -- the same
    // reasoning as throwImp() above.
    void shedArmour() {
        for (models.entities.zombies.Zombie zombie : session.getMap().getRow(2).getZombies()) {
            if (!zombie.getHealth().hasArmor()) {
                continue;
            }
            int before = zombie.getHealth().getTotalHP();
            // Everything except a sliver of body, so the armour is destroyed AND the body ends up
            // under the half that sheds the arm.
            int leave = 20;
            zombie.getHealth().applyDamage(Math.max(1, before - leave),
                    models.entities.projectiles.Element.NEUTRAL, null);
            com.badlogic.gdx.Gdx.app.log("Showcase", "stripped " + zombie.getAlias() + " in row 2: "
                    + before + " -> " + zombie.getHealth().getTotalHP()
                    + " HP -- its armour should fly off and it should walk on with a bone arm");
        }
    }

    // One zombie turned round, so the mirrored art can be compared with the ones beside it.
    //
    // Hypnosis is otherwise almost impossible to stage: it happens when a zombie EATS a Hypno-shroom,
    // which means waiting for one to reach the plant and chew through it. The flag is set directly
    // here rather than through a command because there is no cheat for it -- and the flag is all the
    // renderer reads, so this shows exactly what a real hypnosis would.
    private void hypnotiseOne() {
        for (models.entities.zombies.Zombie zombie : session.getMap().getRow(0).getZombies()) {
            zombie.getState().setHypnotized(true);
            com.badlogic.gdx.Gdx.app.log("Showcase", "hypnotised " + zombie.getAlias()
                    + " in row 0 -- it should be MIRRORED and walking right");
            return;
        }
    }

    // One plant with an octopus on it (T8.4).
    //
    // Staged directly for the same reason hypnosis is: there is no cheat for it, and the real route is
    // a Beach-only ThrowOctopusAbility landing on a lane this board does not have. The flag and the HP
    // are all the renderer reads, so this shows exactly what a real snare would.
    private void snareOne() {
        for (models.map.Cell cell : session.getMap().getRow(2).getCells()) {
            models.entities.plants.Plant plant = cell.getCurrentPlant();
            if (plant == null || plant.isDead() || plant.hasOctopus()) {
                continue;
            }
            plant.bindWithOctopus();
            com.badlogic.gdx.Gdx.app.log("Showcase", "snared " + plant.getName() + " at ("
                    + (int) plant.getX() + ", " + plant.getY()
                    + ") -- an octopus should be clinging to it");
            return;
        }
    }

    // A Gargantuar already hurt enough to throw its Imp, in row 3.
    //
    // Row 3 and not row 4: the Jalapeno above burns row 4 end to end within the first second, and a
    // Gargantuar standing there at half health was incinerated before it ever took its turn. Two runs
    // of "the throw never fires" were that and nothing else.
    //
    // ThrowImp fires when the Gargantuar drops to HALF its starting health, and a 3600-HP Gargantuar is
    // several minutes of shooting away from that -- so the throw, `fire` + `cannon_fire` on the thrower
    // and `fly` + `land` on the Imp, is unreachable in any run short enough to screenshot.
    //
    // Damage is dealt through applyDamage rather than by writing the HP field, so this is a zombie that
    // has genuinely been shot: the layers peel in the normal order and the ABILITY still decides for
    // itself whether to throw, on its own rule. Same shape as hypnotiseOne() above and for the same
    // reason -- there is no cheat command for either.
    private void throwImp() {
        engine.submitInGameCommand("cheat spawn-zombie -t ZombieGargantuar -l (7, 3)");
        for (models.entities.zombies.Zombie zombie : session.getMap().getRow(3).getZombies()) {
            if (!"ZombieGargantuar".equalsIgnoreCase(zombie.getAlias())) {
                continue;
            }
            // Just past half, so the threshold in ThrowImp is crossed on the next tick.
            int toHalf = zombie.getHealth().getMaxTotalHp() / 2 + 1;
            zombie.getHealth().applyDamage(toHalf, models.entities.projectiles.Element.NEUTRAL, null);
            com.badlogic.gdx.Gdx.app.log("Showcase", "Gargantuar in row 3 shot down to "
                    + zombie.getHealth().getTotalHP() + " HP -- it should throw its Imp to column 2");
            return;
        }
    }

    // Three sun values side by side in row 4, so the size difference is a comparison rather than a
    // memory, plus a radioactive one which draws from its own SUN_BOMB animation.
    private void makeSuns() {
        addSun(1, 4, SunType.NORMAL, utils.Constants.NORMAL_SUN_AMOUNT);       // 25 -- smallest
        addSun(3, 4, SunType.NORMAL, 50);                                      // 50 -- the reference
        addSun(5, 4, SunType.SPECIAL, utils.Constants.SPECIAL_SUN_AMOUNT);     // 100 -- bigger

        // The radioactive one has to FALL, because falling is the whole mechanic: caught in the air it
        // detonates, allowed to land it turns into an ordinary 50. A stationary one shows neither.
        session.addSun(new Sun(7.5, 0, 4, SunType.RADIOACTIVE,
                utils.Constants.RADIOACTIVE_SUN_AMOUNT, true, 6000));
    }

    private void addSun(int col, int row, SunType type, int amount) {
        // Not falling, so they sit still and can be compared; expiry is long enough to look at them.
        session.addSun(new Sun(col + 0.5, row, row, type, amount, false, 6000));
    }

    // Fed a moment later than everything else: the Snow Pea has to exist and have zombies in its lane
    // before the lane freeze has anything to show.
    void feedSnowPea() {
        engine.submitInGameCommand("cheat add-plant-food");
        engine.submitInGameCommand("feed plant -l (1, 1)");

        int frozen = 0;
        int targetable = 0;
        int total = 0;
        for (models.entities.zombies.Zombie z : session.getMap().getRow(1).getZombies()) {
            total++;
            if (z.isTargetable()) {
                targetable++;
            }
            if (z.getState().isFrozen()) {
                frozen++;
            }
        }
        com.badlogic.gdx.Gdx.app.log("LaneFreeze", "row 1: " + total + " zombies, "
                + targetable + " targetable, " + frozen + " frozen");
    }

    // Catches the radioactive sun while it is still in the air, which is the only state in which it
    // detonates. Reports what the model did so the blast can be told apart from a plain collection.
    void detonateRadioactiveSun() {
        Sun target = null;
        for (Sun sun : session.getActiveSuns()) {
            if (sun.getType() == SunType.RADIOACTIVE && sun.isFalling() && !sun.isRemovable()) {
                target = sun;
                break;
            }
        }
        if (target == null) {
            com.badlogic.gdx.Gdx.app.log("SunBomb", "no falling radioactive sun to catch");
            return;
        }
        int col = (int) Math.floor(target.getX());
        int row = (int) Math.floor(target.getTargetY());

        // Health before and after, so the 5x5 zombie burst can be seen as a number rather than
        // inferred from whether something looked hurt.
        int centreRow = target.getY();
        java.util.Map<models.entities.zombies.Zombie, Integer> before = new java.util.HashMap<>();
        for (models.map.Row r : session.getMap().getRows()) {
            for (models.entities.zombies.Zombie z : r.getZombies()) {
                before.put(z, z.getHealth().getTotalHP());
            }
        }

        boolean ok = engine.submitInGameCommand("collect sun -l (" + col + ", " + row + ")");

        int hurt = 0;
        int totalDamage = 0;
        for (java.util.Map.Entry<models.entities.zombies.Zombie, Integer> e : before.entrySet()) {
            int lost = e.getValue() - e.getKey().getHealth().getTotalHP();
            if (lost > 0) {
                hurt++;
                totalDamage += lost;
            }
        }
        com.badlogic.gdx.Gdx.app.log("SunBomb", "caught mid-air at (" + col + ", " + row
                + ") centre row " + centreRow + " = " + ok
                + "  |  zombies hurt: " + hurt + ", total damage: " + totalDamage
                + " (expect " + utils.Constants.RADIOACTIVE_ZOMBIE_DAMAGE + " each, 5x5)");
    }
}
