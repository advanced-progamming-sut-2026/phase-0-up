package factories;

import factories.plant.EffectiveStats;
import factories.plant.FoodStrategyFactory;
import factories.plant.PlantAbilityFactory;
import factories.plant.UpgradeResolver;
import models.entities.plants.Plant;
import models.entities.plants.PlantTags;
import models.entities.plants.abilities.PlantAbility;
import models.entities.plants.components.PlantHealthComponent;
import models.entities.plants.components.StackableComponent;
import models.templates.PlantTemplate;
import models.templates.PlantTemplate.ExtraAbilitySpec;
import utils.Constants;
import utils.registry.PlantRegistry;

// Spawns a fresh, grid-ready Plant from a registered blueprint. Rather than cloning a prototype
// (which would need deep-copying each ability's mutable per-tick state), every call builds brand-new
// abilities from the template, folds in the requested level's upgrades, and wires up plant food.
//
// Handled data-driven notes / known limitations:
//   * recharge lives on the seed packet, not the Plant (see UpgradeResolver.effectiveRecharge).
//   * MODIFIER_UTILITY (Imitater) and AUTO_PLANTFOOD_ON_ENTER / RESET_FAMILY_COOLDOWNS are seed-layer
//     concerns and intentionally build no in-grid ability here.
//   * A few SPECIAL_MECHANIC tags depend on subsystems that don't exist yet and are no-ops for now:
//     CHILL_DURATION_EXT / POISON_TICK_BUFF (projectiles carry no status duration), GROWTH_STAGE_MAX_UP
//     (no data for a 4th stage), GRAPE_BOUNCE_EXT, WARM_RADIUS_EXT (Pepper-pult has no warmth ability).
//   * Sea/Puff-shroom have a 60s lifespan in the design that the JSON does not encode (no lifespan field).
public final class PlantFactory {
    private PlantFactory() { }

    public static Plant createPlant(String plantName, int level, int x, int y) {
        PlantTemplate template = PlantRegistry.getInstance().getTemplateByName(plantName);
        if (template == null) {
            return null;
        }

        EffectiveStats stats = UpgradeResolver.resolve(template, level);
        PlantHealthComponent health = new PlantHealthComponent(stats.getMaxHp());
        boolean aquatic = hasTag(template, PlantTags.WATER);

        Plant plant = new Plant(template.getName(), template.getId(), x + 0.5, y,
                health, level, stats.getCost(), aquatic);
        plant.setCategory(template.getCategory().name());
        plant.setProtector(template.isProtector());
        plant.setPlatform(template.isPlatform());

        applyTags(plant, template);
        buildAbilities(plant, template, stats);
        applyStackable(plant, template);
        plant.setPlantFoodStrategy(FoodStrategyFactory.build(template.getPlantFood()));
        UpgradeResolver.applySpecialMechanics(plant, stats.getSpecialMechanics());

        return plant;
    }

    private static void buildAbilities(Plant plant, PlantTemplate template, EffectiveStats stats) {
        int scalarDamage = template.getDamage() + stats.getDamageBuff();

        PlantAbility base = PlantAbilityFactory.build(template.getAbilityType(), template.getAbilityParams(),
                stats.getActionIntervalTicks(), scalarDamage, stats.getDamageBuff());
        if (base != null) {
            plant.addAbility(base);
        }

        if (template.getExtraAbilities() != null) {
            for (ExtraAbilitySpec extra : template.getExtraAbilities()) {
                int intervalTicks = (int) Math.round(extra.getActionInterval() * Constants.TICKS_PER_SECOND);
                PlantAbility ability = PlantAbilityFactory.build(extra.getAbilityType(), extra.getAbilityParams(),
                        intervalTicks, scalarDamage, stats.getDamageBuff());
                if (ability != null) {
                    plant.addAbility(ability);
                }
            }
        }
    }

    private static void applyTags(Plant plant, PlantTemplate template) {
        if (template.getTags() == null) {
            return;
        }
        for (String raw : template.getTags()) {
            PlantTags tag = PlantTags.fromJson(raw);
            if (tag != null) {
                plant.getTags().add(tag);
            }
        }
    }

    // Head-stacking plants (Pea Pod) gain a StackableComponent; its cap comes from abilityValue.
    // Cover/platform plants also carry the STACK tag but stack via the protector/platform mechanic.
    private static void applyStackable(Plant plant, PlantTemplate template) {
        boolean stacks = hasTag(template, PlantTags.STACK)
                && !template.isProtector() && !template.isPlatform();
        int maxStacks = (int) template.getAbilityValue();
        if (stacks && maxStacks > 1) {
            plant.setStackableComponent(new StackableComponent(plant, maxStacks, 0));
        }
    }

    private static boolean hasTag(PlantTemplate template, PlantTags target) {
        if (template.getTags() == null) {
            return false;
        }
        for (String raw : template.getTags()) {
            if (PlantTags.fromJson(raw) == target) {
                return true;
            }
        }
        return false;
    }
}
