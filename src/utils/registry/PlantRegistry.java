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

    private PlantRegistry() { }

    public static PlantRegistry getInstance() {
        if (instance == null) {
            instance = new PlantRegistry();
        }
        return instance;
    }

    public PlantTemplate getTemplateByName(String plantName) {
        return plantTemplatesByName.get(plantName);
    }

    public PlantTemplate getTemplateById(int id) {
        return plantTemplatesById.get(id);
    }

    public Map<String, PlantTemplate> getAllPlantTemplates() {
        return plantTemplatesByName;
    }

    public void register(PlantTemplate plantTemplate) {
        if (plantTemplate != null && plantTemplate.getName() != null) {
            plantTemplatesByName.put(plantTemplate.getName(), plantTemplate);
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
