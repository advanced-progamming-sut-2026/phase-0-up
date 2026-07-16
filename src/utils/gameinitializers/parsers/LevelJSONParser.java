package utils.gameinitializers.parsers;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import models.templates.LevelTemplate;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;

// Reads data/levels.json into a flat list of LevelTemplate blueprints. The nested "rules"/"waves"
// objects deserialize in the same Gson pass; SCREAMING_SNAKE mode/chapter strings map onto enums
// later in LevelFactory, mirroring the plant and zombie parsers.
public class LevelJSONParser implements Parser<LevelTemplate> {
    private final Gson gson = new Gson();

    @Override
    public List<LevelTemplate> parse(String filePath) {
        try (Reader reader = new FileReader(filePath)) {
            Type listType = new TypeToken<List<LevelTemplate>>() { }.getType();
            List<LevelTemplate> templates = gson.fromJson(reader, listType);
            return templates != null ? templates : Collections.emptyList();
        } catch (IOException e) {
            System.err.println("Failed to load level data from " + filePath + ": " + e.getMessage());
            return Collections.emptyList();
        }
    }
}
