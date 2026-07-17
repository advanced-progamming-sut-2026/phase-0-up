package controllers.systems.game;

import models.entities.collectibles.Sun;
import models.entities.collectibles.SunType;
import models.entities.plants.Plant;
import models.entities.projectiles.Element;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import models.game.gamemodes.GameMode;
import models.game.gamemodes.NightOpsMode;
import models.map.Cell;
import models.map.Row;
import utils.Constants;
import utils.Result;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class SunSystem {
    private static final int SKY_SUN_GROUND_EXPIRE_TICKS = 100;
    private static final int PLANT_SUN_NEVER_EXPIRE_TICKS = Integer.MAX_VALUE;


    private final Random random = new Random();
    private long lastSkySunSpawnTick = -1;
    private static final double SPECIAL_THRESHOLD =
            Constants.NORMAL_SUN_PROBABILITY + Constants.SPECIAL_SUN_PROBABILITY;


    public void onTick(GameSession gameSession) {
        maybeSpawnSkySun(gameSession);
        updateActiveSuns(gameSession);
    }

    public double dropRate(long elapsedTime){
        return Math.max(6 + 0.05 * elapsedTime , 12);
    }

    public Result SpawnSkySun(GameSession gameSession){
        if(!canSpawnSkySun(gameSession)){
            return null;
        }
        int column = random.nextInt(Constants.BOARD_COLS);
        int row = random.nextInt(Constants.BOARD_ROWS);

        double targetX = column + 0.5 + (random.nextDouble() - 0.5) * 0.5;
        double targetY = row + random.nextDouble() * 0.6;
        double startY = targetY - getFallDistance();

        SunType type = rollType(random);
        int amount = amountForType(type);

        Sun sun = new Sun(targetX , startY , targetY , type , amount , true,SKY_SUN_GROUND_EXPIRE_TICKS);
        gameSession.addSun(sun);

        return new Result(true , "New " + formatSunType(type) +
                " sun is dropping at position (" + column + ", " + row + ")");

    }

    public void SpawnPlantSun(GameSession gameSession, double x, int y, int amount) {
        Sun sun = new Sun(x, y, y, SunType.NORMAL, amount, false, PLANT_SUN_NEVER_EXPIRE_TICKS);
        gameSession.addSun(sun);
    }

    private String formatSunType(SunType type) {
        return type.name().toLowerCase();
    }

    public static SunType rollType(Random random) {
        double roll = random.nextDouble();

        if (roll < Constants.NORMAL_SUN_PROBABILITY) {
            return SunType.NORMAL;
        }
        if (roll < SPECIAL_THRESHOLD) {
            return SunType.SPECIAL;
        }
        return SunType.RADIOACTIVE;
    }

    public static int amountForType(SunType type) {
        switch (type) {
            case SPECIAL:
                return Constants.SPECIAL_SUN_AMOUNT;
            case RADIOACTIVE:
                return Constants.RADIOACTIVE_SUN_AMOUNT;
            case NORMAL:
            default:
                return Constants.NORMAL_SUN_AMOUNT;
        }
    }

    private double getFallDistance(){
        return Constants.SUN_FALL_DURATION_SECONDS * Constants.TICKS_PER_SECOND *0.05;
    }

    private boolean canSpawnSkySun(GameSession gameSession){
        GameMode mode = gameSession.getMode();
        // Ask the mode: the level's own rule (levels.json "disableSkySun") wins over the chapter
        // default below. Testing instanceof here ignored the flag and hard-coded the answer.
        if(mode != null && !mode.allowsSkySun()){
            return false;
        }
        if(gameSession.getLevel() == null || gameSession.getLevel().getTemplate() == null){
            return true;
        }
        String chapter = gameSession.getLevel().getTemplate().getChapter();
        if(chapter == null) {
            return true;
        }
        String normalChapter = chapter.toLowerCase().replace(' ', '-');
        return !normalChapter.contains("dark");
    }

    public void reset() {
        lastSkySunSpawnTick = -1;
    }

    public boolean collectSun(GameSession gameSession, double x, int y){
        Sun sun = findSunAt(gameSession , x , y );
        if(sun == null || sun.isRemovable()){
            return false;
        }
        if(sun.getType() == SunType.RADIOACTIVE && sun.isFalling()){
            triggerRadioactiveExplosion(gameSession,sun);
        }

        sun.collect(gameSession);
        gameSession.getActiveSuns().remove(sun);
        return true;
    }

    private void triggerRadioactiveExplosion(GameSession gameSession, Sun sun) {
        applyZombieAoE(gameSession, sun.getX(), sun.getY(), Constants.RADIOACTIVE_ZOMBIE_DAMAGE);
        applyPlantAoE(gameSession, sun.getX(), sun.getY(), Constants.RADIOACTIVE_PLANT_DAMAGE);
    }

    private void applyZombieAoE(GameSession gameSession, double centerX, int centerY, int damage) {
        int radius = Constants.RADIOACTIVE_ZOMBIE_AOE_SIZE / 2;
        int centerColumn = columnFromX(centerX);

        for (Row row : gameSession.getMap().getRows()) {
            int rowIndex = row.getIndex();
            if (Math.abs(rowIndex - centerY) > radius) {
                continue;
            }

            for (Zombie zombie : row.getZombies()) {
                if (zombie.getHealth() == null || zombie.getHealth().isDead()) {
                    continue;
                }
                if (zombie.getMovement() == null) {
                    continue;
                }

                int zombieColumn = columnFromX(zombie.getMovement().getPositionX());
                if (Math.abs(zombieColumn - centerColumn) <= radius) {
                    zombie.getHealth().applyDamage(damage, Element.NEUTRAL,null);
                }
            }
        }
    }

    private void applyPlantAoE(GameSession gameSession, double centerX, int centerY, int damage) {
        int radius = Constants.RADIOACTIVE_PLANT_AOE_SIZE / 2;
        int centerColumn = columnFromX(centerX);

        for (Row row : gameSession.getMap().getRows()) {
            int rowIndex = row.getIndex();
            if (Math.abs(rowIndex - centerY) > radius) {
                continue;
            }

            for (Cell cell : row.getCells()) {
                int cellColumn = columnFromX(cell.getX());
                if (Math.abs(cellColumn - centerColumn) > radius) {
                    continue;
                }

                if (!cell.hasPlant()) {
                    continue;
                }

                Plant plant = cell.getCurrentPlant();
                if (plant.getHealth() != null) {
                    plant.getHealth().takeDamage(damage);
                }
            }
        }
    }

    private Sun findSunAt(GameSession gameSession, double x, int y) {
        int targetColumn = (int) x;
        Sun fallingCandidate = null;

        for (Sun sun : gameSession.getActiveSuns()) {
            if (sun.isRemovable()) {
                continue;
            }

            if (columnFromX(sun.getX()) != targetColumn) {
                continue;
            }

            if (!sun.isFalling() && sun.getY() == y) {
                return sun;
            }

            if (sun.isFalling() && fallingCandidate == null && targetRow(sun) == y) {
                fallingCandidate = sun;
            }
        }

        return fallingCandidate;
    }

    private int columnFromX(double x) {
        return (int) Math.floor(x);
    }

    private int targetRow(Sun sun) {
        return (int) Math.floor(sun.getTargetY());
    }

    private Result maybeSpawnSkySun(GameSession gameSession){
        if (!canSpawnSkySun(gameSession)) {
            return null;
        }

        long currentTick = gameSession.getTimeTicks();
        long elapsedSeconds = currentTick/Constants.TICKS_PER_SECOND;
        double intervalSeconds = dropRate(elapsedSeconds) * getDifficultyIntervalMultiplier(gameSession);
        long intervalTicks = Math.max(1 , Math.round(intervalSeconds * Constants.TICKS_PER_SECOND));

        if(lastSkySunSpawnTick < 0){
            lastSkySunSpawnTick = currentTick;
            return null;
        }

        if(currentTick - lastSkySunSpawnTick >= intervalTicks){
            lastSkySunSpawnTick = currentTick;
            return SpawnSkySun(gameSession);
        }
        return null;
    }

    private double getDifficultyIntervalMultiplier(GameSession gameSession) {
        int difficultyLevel = gameSession.getPlayer().getDifficultyLevel();
        return difficultyLevel / 3.0;
    }

    private List<Result> updateActiveSuns(GameSession gameSession){
        List<Result> results = new ArrayList<>();
        Iterator<Sun> iterator = gameSession.getActiveSuns().iterator();

        while(iterator.hasNext()){
            Sun sun = iterator.next();
            boolean wasFalling = sun.isFalling();
            sun.update(gameSession);
            if(wasFalling && !sun.isFalling()){
                int column = columnFromX(sun.getX());
                results.add(new Result(true ,
                        "Sun reached the ground at position (" + column + ", " + sun.getY() + ")"));
                if(sun.getType() == SunType.RADIOACTIVE){
                    replaceSun(gameSession , sun , createGroundNormalSun(sun));
                    continue;
                }
            }

            if(sun.isRemovable()){
                iterator.remove();
            }
        }
        return results;
    }


    private Sun createGroundNormalSun(Sun radioactiveSun) {
        return new Sun(
                radioactiveSun.getX(),
                radioactiveSun.getY(),
                radioactiveSun.getY(),
                SunType.NORMAL,
                Constants.NORMAL_SUN_AMOUNT,
                false,
                SKY_SUN_GROUND_EXPIRE_TICKS
        );
    }

    private void replaceSun(GameSession gameSession, Sun oldSun, Sun newSun) {
        List<Sun> activeSuns = gameSession.getActiveSuns();
        int index = activeSuns.indexOf(oldSun);
        if (index >= 0) {
            activeSuns.set(index, newSun);
        }
    }
}
