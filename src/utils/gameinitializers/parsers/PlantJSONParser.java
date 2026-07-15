package utils.gameinitializers.parsers;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import models.templates.PlantTemplate;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;

// Reads data/plants.json and deserializes it into a flat list of PlantTemplate blueprints.
// SCREAMING_SNAKE strings in the file (category, abilityType, food/upgrade types, elements, ...)
// map straight onto their Java enum constants by name, so a single Gson pass fills the whole graph.
public class PlantJSONParser implements Parser<PlantTemplate> {
    private final Gson gson = new Gson();

    @Override
    public List<PlantTemplate> parse(String filePath) {
        try (Reader reader = new FileReader(filePath)) {
            Type listType = new TypeToken<List<PlantTemplate>>() { }.getType();
            List<PlantTemplate> templates = gson.fromJson(reader, listType);
            return templates != null ? templates : Collections.emptyList();
        } catch (IOException e) {
            System.err.println("Failed to load plant data from " + filePath + ": " + e.getMessage());
            return Collections.emptyList();
        }
    }
}
