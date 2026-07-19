package utils.registry;

import models.templates.QuestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Central store of quest blueprints, keyed by id. Insertion order is preserved so the travel log can
// fall back to authored order within a priority tier.
public class QuestRegistry {
    private static QuestRegistry instance;
    private final Map<String, QuestTemplate> questTemplatesById = new LinkedHashMap<>();

    private QuestRegistry() { }

    public static QuestRegistry getInstance() {
        if (instance == null) {
            instance = new QuestRegistry();
        }
        return instance;
    }

    public List<QuestTemplate> getAllQuestTemplates() {
        return new ArrayList<>(questTemplatesById.values());
    }

    public QuestTemplate getQuestTemplateById(String id) {
        return id == null ? null : questTemplatesById.get(id);
    }

    public void register(QuestTemplate template) {
        if (template != null && template.getId() != null) {
            questTemplatesById.put(template.getId(), template);
        }
    }

    public void registerAll(List<QuestTemplate> templates) {
        if (templates == null) {
            return;
        }
        for (QuestTemplate template : templates) {
            register(template);
        }
    }
}
