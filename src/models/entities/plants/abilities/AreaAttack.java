package models.entities.plants.abilities;

import models.entities.plants.Plant;
import models.entities.projectiles.Element;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import utils.Constants;

import java.util.List;

// Damages every live zombie -- and everything else breakable -- within a (rowRadius x colRadius) tile
// area around a plant.
public final class AreaAttack {
    private AreaAttack() { }

    public static void strike(GameSession gameSession, Plant source, int rowRadius, int colRadius,
                              int damage, Element element) {
        for (int rowOffset = -rowRadius; rowOffset <= rowRadius; rowOffset++) {
            int row = source.getY() + rowOffset;
            if (row < 0 || row >= Constants.BOARD_ROWS) continue;

            List<Zombie> zombies = gameSession.getMap().getRow(row).getZombies();
            if (zombies != null) {
                for (Zombie z : zombies) {
                    if (!z.isTargetable()) continue;
                    if (Math.abs(z.getMovement().getPositionX() - source.getX()) <= colRadius + 0.5) {
                        z.getHealth().applyDamage(damage, element, source);
                    }
                }
            }
            strikeGround(gameSession, source, row, colRadius, damage, element);
        }
    }

    // The blast reaching what is standing on the tiles rather than walking over them.
    //
    // This pass did not exist, and a blast is the ONLY heat some fire plants ever produce: a Jalapeno
    // burns its whole lane and is consumed doing it, so EnvironmentSystem's ambient melt -- which wants
    // a fire plant still standing next to the ice -- can never fire for one. The result was a Jalapeno
    // detonating along a row of ice blocks and leaving every one of them frozen solid.
    //
    // Terrain decides for itself what a hit means, exactly as it does for a projectile: FrozenTerrain
    // melts outright on FIRE and chips on anything else, ICE does nothing to it, and a headstone in
    // range now takes the blast the way it already takes a pea. Everything else -- craters, water,
    // sand, cursed ground -- inherits Terrain's no-op and is untouched.
    private static void strikeGround(GameSession gameSession, Plant source, int row, int colRadius,
                                     int damage, Element element) {
        models.map.Row lane = gameSession.getMap().getRow(row);
        if (lane == null || lane.getCells() == null) {
            return;
        }
        for (models.map.Cell cell : lane.getCells()) {
            if (cell == null || Math.abs(cell.getX() - source.getX()) > colRadius) {
                continue;
            }
            // A plant frozen in play carries its ice on ITSELF and adds no terrain, so it has to be
            // thawed by name or a Jalapeno would free every block in the lane except the ones holding
            // its neighbours. Never the source: a bomb does not put itself out.
            models.entities.plants.Plant standing = cell.getCurrentPlant();
            if (standing != null && standing != source && standing.isFrozen()) {
                standing.damageIceBlock(damage, element);
            }
            if (cell.getTerrain() == null) {
                continue;
            }
            for (models.map.Terrains.Terrain terrain : new java.util.ArrayList<>(cell.getTerrain())) {
                if (!terrain.isDestroyed()) {
                    terrain.takeDamage(damage, element);
                }
            }
        }
    }
}
