package utils.gameinitializers.parsers;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import models.templates.QuestTemplate;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;

// Reads data/quests.json into a flat list of QuestTemplate blueprints (the English data transformed
// from documents/quests.xlsx). The nested "reward" object deserializes in the same Gson pass; the
// category/priority strings map onto their enums later in QuestFactory, mirroring the other parsers.
public class QuestJSONParser implements Parser<QuestTemplate> {
    private final Gson gson = new Gson();

    @Override
    public List<QuestTemplate> parse(String filePath) {
        try (Reader reader = new FileReader(filePath)) {
            Type listType = new TypeToken<List<QuestTemplate>>() { }.getType();
            List<QuestTemplate> templates = gson.fromJson(reader, listType);
            return templates != null ? templates : Collections.emptyList();
        } catch (IOException e) {
            System.err.println("Failed to load quest data from " + filePath + ": " + e.getMessage());
            return Collections.emptyList();
        }
    }
}
