package models.entities.plants.abilities;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.game.GameSession;

// Hypnotizes the zombie that bites the plant, then the plant is consumed (Hypno-shroom).
public class HypnotizeOnEatenAbility extends PlantAbility {
    private double zombieHealthMultiplier = 1.0;
    private double zombieDamageMultiplier = 1.0;

    public HypnotizeOnEatenAbility() {
        super(0, null); // reacts to being eaten, not the tick loop
    }

    // Upgrades (ZOMBIE_HEALTH_MULTIPLIER / ZOMBIE_DAMAGE_MULTIPLIER): buff the hypnotized ally.
    public void setZombieHealthMultiplier(double multiplier) {
        this.zombieHealthMultiplier = multiplier;
    }

    public void setZombieDamageMultiplier(double multiplier) {
        this.zombieDamageMultiplier = multiplier;
    }

    public double getZombieHealthMultiplier() {
        return zombieHealthMultiplier;
    }

    public double getZombieDamageMultiplier() {
        return zombieDamageMultiplier;
    }

    @Override
    public void execute(Plant owner, GameSession gameSession) {
        // no tick behavior
    }

    // Plant food: the next zombie to bite this shroom does not merely change sides -- it swells into a
    // Gargantuar first. Armed rather than applied, because the effect belongs to the BITE, and nothing
    // may have bitten yet when the food is eaten.
    private boolean gargantuarArmed;

    public void armGargantuar() {
        this.gargantuarArmed = true;
    }

    // Armed counts as a boost still running, so the plant keeps its glow until something walks into it.
    // That is the only cue the player has that the shroom is loaded -- it looks identical otherwise.
    @Override
    public boolean isPlantFoodBusy() {
        return gargantuarArmed;
    }

    @Override
    public void onOwnerEaten(Plant owner, Zombie eater, GameSession gameSession) {
        if (!gargantuarArmed || !swellIntoGargantuar(eater, gameSession)) {
            eater.getState().setHypnotized(true);
            eater.applyHypnoBuffs(zombieHealthMultiplier, zombieDamageMultiplier);
        }
        if (owner.getHealth() != null) {
            owner.getHealth().takeDamage(owner.getHealth().getMaxHp());
        }
    }

    // Swaps the eater for a hypnotised Gargantuar standing where it stood.
    //
    // A swap rather than a mutation because a Zombie's alias is fixed at birth and the alias is what
    // chooses its art -- a browncoat given Gargantuar health would still be drawn as a browncoat, which
    // is the one thing this effect has to show.
    //
    // Both list edits are safe here: CombatSystem.updateZombieStates walks a COPY of each row, so a
    // zombie removed or added during the pass cannot disturb it. Removed rather than damaged to death,
    // so the player is not paid a kill and a loot roll for a zombie that just joined their side.
    //
    // Returns false if the Gargantuar could not be built, in which case the caller falls back to
    // ordinary hypnosis rather than letting the bite do nothing at all.
    private boolean swellIntoGargantuar(Zombie eater, GameSession gameSession) {
        int row = eater.getMovement().getPositionY();
        if (row < 0 || row >= utils.Constants.BOARD_ROWS) {
            return false;
        }
        Zombie giant = factories.ZombieFactory.createZombie("ZombieGargantuar",
                eater.getMovement().getPositionX(), row, gameSession);
        if (giant == null) {
            return false;
        }
        giant.getState().setHypnotized(true);
        giant.applyHypnoBuffs(zombieHealthMultiplier, zombieDamageMultiplier);

        gameSession.getMap().getRow(row).getZombies().remove(eater);
        gameSession.getMap().getRow(row).getZombies().add(giant);
        gargantuarArmed = false;

        gameSession.reportEvent("The " + eater.getAlias() + " swallows the hypno-shroom whole and"
                + " swells into a Gargantuar -- and it is on YOUR side now!");
        return true;
    }
}
