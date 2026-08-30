package utils.registry;

import models.templates.PlantTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Central store of plant blueprints, indexed by both name and id. Populated once at startup
// (see PlantInitializer); PlantFactory reads from it to spawn live plants on demand.
public class PlantRegistry {
    private static PlantRegistry instance;

    private final Map<String, PlantTemplate> plantTemplatesByName = new HashMap<>();
    private final Map<Integer, PlantTemplate> plantTemplatesById = new HashMap<>();
    // Callers disagree on casing: levels.json and the menus use the display name ("Wall-nut"), while
    // Profile.unlockPlant stores it lower-cased. This index makes every lookup case-insensitive.
    private final Map<String, PlantTemplate> plantTemplatesByLowerName = new HashMap<>();

    private PlantRegistry() { }

    public static PlantRegistry getInstance() {
        if (instance == null) {
            instance = new PlantRegistry();
        }
        return instance;
    }

    public PlantTemplate getTemplateByName(String plantName) {
        if (plantName == null) {
            return null;
        }
        PlantTemplate exact = plantTemplatesByName.get(plantName);
        return exact != null ? exact : plantTemplatesByLowerName.get(normalize(plantName));
    }

    public static String normalize(String plantName) {
        return plantName == null ? null : plantName.toLowerCase().trim();
    }

    public PlantTemplate getTemplateById(int id) {
        return plantTemplatesById.get(id);
    }

    // The Imitater, or null if this roster has none.
    //
    // Found by its ABILITY rather than by its name: it is the one plant whose type is MODIFIER_UTILITY,
    // the type PlantAbilityFactory deliberately builds nothing for, because picking the Imitater is not
    // picking a plant at all -- it is picking a second packet of something else. Asked here rather than
    // spelled out at each of the three call sites (the toggle command, seed selection, the seed bank)
    // so a rename in plants.json cannot leave two of them believing different things.
    public PlantTemplate getImitaterTemplate() {
        for (PlantTemplate template : plantTemplatesByName.values()) {
            if (template != null
                    && template.getAbilityType() == models.templates.AbilityType.MODIFIER_UTILITY) {
                return template;
            }
        }
        return null;
    }

    public boolean isImitater(String plantName) {
        PlantTemplate template = getTemplateByName(plantName);
        return template != null
                && template.getAbilityType() == models.templates.AbilityType.MODIFIER_UTILITY;
    }

    public Map<String, PlantTemplate> getAllPlantTemplates() {
        return plantTemplatesByName;
    }

    public void register(PlantTemplate plantTemplate) {
        if (plantTemplate != null && plantTemplate.getName() != null) {
            plantTemplatesByName.put(plantTemplate.getName(), plantTemplate);
            plantTemplatesByLowerName.put(normalize(plantTemplate.getName()), plantTemplate);
            plantTemplatesById.put(plantTemplate.getId(), plantTemplate);
        }
    }

    public void registerAll(List<PlantTemplate> templates) {
        if (templates == null) {
            return;
        }
        for (PlantTemplate template : templates) {
            register(template);
        }
    }
}
