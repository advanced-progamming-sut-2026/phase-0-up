package utils.registry;

import models.templates.PlantTemplate;

import java.util.HashMap;
import java.util.Map;

public class PlantRegistry {
    private static PlantRegistry instance;
    private Map<String, PlantTemplate> plantTemplatesByName = new HashMap<>();

    private PlantRegistry(){}
    public static PlantRegistry getInstance(){
        if (instance == null){
            instance = new PlantRegistry();
        }
        return instance;
    }

    public PlantTemplate getTemplateByName(String plantName){return plantTemplatesByName.get(plantName);}
    public Map<String, PlantTemplate> getAllPlantTemplates(){
        return plantTemplatesByName;
    }

    public void register(PlantTemplate plantTemplate){
        if (plantTemplate != null && plantTemplate.getName() != null){
            plantTemplatesByName.put(plantTemplate.getName(), plantTemplate);
        }
    }

}
