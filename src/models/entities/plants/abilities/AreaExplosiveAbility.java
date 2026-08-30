package models.entities.plants.abilities;

import models.entities.plants.Plant;
import models.entities.plants.abilities.triggers.TriggerStrategy;
import models.entities.projectiles.Element;
import models.game.GameSession;

// Base for plants that detonate: deals area burst damage around the plant, then consumes it.
public abstract class AreaExplosiveAbility extends PlantAbility {
    protected int damage;
    protected int explosionRowRadius;
    protected int explosionColRadius;
    protected Element element;

    protected AreaExplosiveAbility(int actionInterval, TriggerStrategy triggerStrategy, int damage,
                                   int explosionRowRadius, int explosionColRadius, Element element) {
        super(actionInterval, triggerStrategy);
        this.damage = damage;
        this.explosionRowRadius = explosionRowRadius;
        this.explosionColRadius = explosionColRadius;
        this.element = element;
    }

    // Upgrade (BONUS_SMASH_CHARGES / BONUS_GRAB_TARGETS): widens the blast so more zombies are caught
    // (Squash crushing two, Tangle Kelp grabbing extra).
    public void widenArea(int extraRowRadius, int extraColRadius) {
        this.explosionRowRadius += extraRowRadius;
        this.explosionColRadius += extraColRadius;
    }

    // Whether this blast scorches the ground it stood on. Doom-shroom only: clearing nearly the whole
    // board is supposed to cost you the tile, and without it the biggest bomb in the game was strictly
    // better than every other one.
    private boolean leavesCrater;

    public void setLeavesCrater(boolean leavesCrater) {
        this.leavesCrater = leavesCrater;
    }

    protected void detonate(Plant owner, GameSession gameSession) {
        AreaAttack.strike(gameSession, owner, explosionRowRadius, explosionColRadius, damage, element);

        // Before the plant is consumed, while its cell is still findable by its own coordinates.
        if (leavesCrater) {
            scorchOwnTile(owner, gameSession);
        }

        // Announce the blast. Every exploding plant funnels through here, so one line covers Cherry
        // Bomb, Jalapeno, Doom-shroom, Potato Mine and the rest.
        //
        // This is also the only signal a renderer can use. A plant being consumed by its own blast is
        // indistinguishable, from the outside, from one being eaten -- both just end up dead and swept
        // on the same tick -- so without saying so here there is no way to know an explosion happened.
        gameSession.reportEvent(owner.getName() + " detonates at ("
                + (int) owner.getX() + ", " + owner.getY() + ")!");

        // the plant is consumed by its own blast
        if (owner.getHealth() != null) {
            owner.getHealth().takeDamage(owner.getHealth().getMaxHp());
        }
    }

    // Burns the tile the bomb stood on. Cell.isPlantable() already refuses any tile carrying a terrain
    // that says it is unplantable, so dropping a CraterTerrain on the cell is the whole rule.
    //
    // Guarded against doubling up, because a crater on a crater would have to be removed twice if the
    // level ever gains a way to clear one.
    private void scorchOwnTile(Plant owner, GameSession gameSession) {
        int row = owner.getY();
        if (row < 0 || row >= utils.Constants.BOARD_ROWS) {
            return;
        }
        models.map.Cell cell = gameSession.getMap().getRow(row).cellAt((int) owner.getX());
        if (cell == null) {
            return;
        }
        for (models.map.Terrains.Terrain terrain : cell.getTerrain()) {
            if (terrain instanceof models.map.Terrains.CraterTerrain) {
                return;
            }
        }
        cell.addTerrain(new models.map.Terrains.CraterTerrain());
        gameSession.reportEvent("The blast leaves a smoking crater at ("
                + (int) owner.getX() + ", " + row + ") -- nothing will grow there now.");
    }
}
