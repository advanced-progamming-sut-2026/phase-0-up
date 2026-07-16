package utils.gameinitializers;

import models.templates.ZombieTemplate;
import utils.gameinitializers.parsers.Parser;
import utils.gameinitializers.parsers.ZombieJSONParser;
import utils.registry.ZombieRegistry;

import java.util.List;

// Loads every zombie blueprint from data/zombie-data/zombies.json into the ZombieRegistry at boot.
public final class ZombieInitializer {
    private static final String ZOMBIES_DATA_PATH = "data/zombie-data/zombies.json";

    private ZombieInitializer() { }

    public static void loadAllZombies() {
        Parser<ZombieTemplate> parser = new ZombieJSONParser();
        List<ZombieTemplate> templates = parser.parse(ZOMBIES_DATA_PATH);
        ZombieRegistry registry = ZombieRegistry.getInstance();
        for (ZombieTemplate template : templates) {
            registry.register(template);
        }
    }
}
