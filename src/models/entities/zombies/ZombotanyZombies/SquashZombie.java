package models.entities.zombies.ZombotanyZombies;

import models.entities.zombies.Abilities.ZombieAbility;
import models.entities.zombies.Components.ArmorType;
import models.entities.zombies.Zombie;
import models.game.GameSession;

import java.util.List;

public class SquashZombie extends Zombie {
    public SquashZombie(int id, String category, int baseHp, List<ArmorType> armorTypes, String alias, int eatDamage, int eatSpeed, double speed, double startX, int startY, boolean canSpawnPlantFood, List<ZombieAbility> abilities, int wavePointCost, boolean glowing, GameSession gameSession) {
        super(id, category, baseHp, armorTypes, alias, eatDamage, eatSpeed, speed, startX, startY, canSpawnPlantFood, abilities, wavePointCost, glowing, gameSession);
    }
}
