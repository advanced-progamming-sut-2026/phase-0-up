package utils.gameinitializers;

import models.templates.PlantTemplate;
import utils.gameinitializers.parsers.Parser;
import utils.gameinitializers.parsers.PlantJSONParser;
import utils.registry.PlantRegistry;

import java.util.List;

// Loads every plant blueprint from data/plants.json into the PlantRegistry. Runs once at boot;
// after this the factory can spawn any plant by name.
public final class PlantInitializer {
    private static final String PLANTS_DATA_PATH = "data/plants.json";

    private PlantInitializer() { }

    public static void loadAllPlants() {
        Parser<PlantTemplate> parser = new PlantJSONParser();
        List<PlantTemplate> templates = parser.parse(PLANTS_DATA_PATH);
        PlantRegistry.getInstance().registerAll(templates);
    }
}
