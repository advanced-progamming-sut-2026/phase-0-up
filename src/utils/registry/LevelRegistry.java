package utils.registry;

import models.templates.LevelTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Central store of level blueprints, keyed by id. Insertion order is preserved so campaign
// assembly can iterate levels in the order they were authored in levels.json.
public class LevelRegistry {
    private static LevelRegistry instance;
    private final Map<String, LevelTemplate> levelTemplatesById = new LinkedHashMap<>();

    private LevelRegistry() { }

    public static LevelRegistry getInstance() {
        if (instance == null) {
            instance = new LevelRegistry();
        }
        return instance;
    }

    public LevelTemplate getLevelTemplateById(String id) {
        return levelTemplatesById.get(id);
    }

    public List<LevelTemplate> getAll() {
        return new ArrayList<>(levelTemplatesById.values());
    }

    public void register(LevelTemplate levelTemplate) {
        if (levelTemplate != null && levelTemplate.getId() != null) {
            levelTemplatesById.put(levelTemplate.getId(), levelTemplate);
        }
    }

    public void registerAll(List<LevelTemplate> templates) {
        if (templates == null) {
            return;
        }
        for (LevelTemplate template : templates) {
            register(template);
        }
    }
}
