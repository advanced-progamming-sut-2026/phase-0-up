package utils.gameinitializers;

import models.templates.QuestTemplate;
import utils.gameinitializers.parsers.Parser;
import utils.gameinitializers.parsers.QuestJSONParser;
import utils.registry.QuestRegistry;

import java.util.List;

// Loads every quest blueprint from data/quests.json into the QuestRegistry at boot, mirroring the
// plant/zombie/level initializers.
public final class QuestInitializer {
    private static final String QUESTS_DATA_PATH = "data/quests.json";

    private QuestInitializer() { }

    public static void loadAllQuests() {
        Parser<QuestTemplate> parser = new QuestJSONParser();
        List<QuestTemplate> templates = parser.parse(QUESTS_DATA_PATH);
        QuestRegistry.getInstance().registerAll(templates);
    }
}
