package utils.gameinitializers;

import factories.LevelFactory;
import models.game.Chapter;
import models.game.EnvironmentType;
import models.game.Level;
import models.templates.LevelTemplate;
import models.user.Profile;
import utils.gameinitializers.parsers.LevelJSONParser;
import utils.gameinitializers.parsers.Parser;
import utils.registry.LevelRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Loads level blueprints from data/levels.json into the LevelRegistry, and rebuilds the live
// Chapter -> Level campaign graph for a profile on demand. Chapters/levels are never persisted
// (only lastChapter/lastLevel are), so a profile's campaign is reconstructed here at login and the
// unlock flags are re-derived from its stored progress.
public final class LevelInitializer {
    private static final String LEVELS_DATA_PATH = "data/levels.json";

    private LevelInitializer() { }

    public static void loadAllLevels() {
        Parser<LevelTemplate> parser = new LevelJSONParser();
        List<LevelTemplate> templates = parser.parse(LEVELS_DATA_PATH);
        LevelRegistry.getInstance().registerAll(templates);
    }

    // How many chapters the registry defines. Used to stop progression from running past the last one.
    public static int chapterCount() {
        Map<String, List<LevelTemplate>> byChapter = new LinkedHashMap<>();
        for (LevelTemplate template : LevelRegistry.getInstance().getAll()) {
            byChapter.computeIfAbsent(chapterKeyOf(template), k -> new ArrayList<>()).add(template);
        }
        return byChapter.size();
    }

    // Builds every chapter from the registry (templates grouped by their "chapter" field, in order)
    // and returns them as fresh, locked Chapters. Callers apply unlock flags via applyProgress.
    public static List<Chapter> buildChapters() {
        Map<String, List<LevelTemplate>> byChapter = new LinkedHashMap<>();
        for (LevelTemplate template : LevelRegistry.getInstance().getAll()) {
            byChapter.computeIfAbsent(chapterKeyOf(template), k -> new ArrayList<>()).add(template);
        }

        List<Chapter> chapters = new ArrayList<>();
        for (Map.Entry<String, List<LevelTemplate>> entry : byChapter.entrySet()) {
            List<Level> levels = new ArrayList<>();
            for (LevelTemplate template : entry.getValue()) {
                Level level = LevelFactory.createLevel(template.getId());
                if (level != null) {
                    levels.add(level);
                }
            }
            chapters.add(new Chapter(entry.getKey(), environmentOf(entry.getKey()),
                    levels.toArray(new Level[0])));
        }
        return chapters;
    }

    // Rebuilds the campaign on the profile and re-derives unlock flags from lastChapter/lastLevel
    // (both 1-based). Runs once when a user becomes active for a session.
    public static void attachCampaign(Profile profile) {
        if (profile == null) {
            return;
        }
        List<Chapter> chapters = buildChapters();
        applyProgress(chapters, profile.getLastChapter(), profile.getLastLevel());

        profile.getUnlockedChapters().clear();
        for (Chapter chapter : chapters) {
            if (chapter.isUnlocked()) {
                profile.addUnlockedChapter(chapter);
            }
        }
        int chapterIndex = Math.min(Math.max(profile.getLastChapter(), 1), chapters.size()) - 1;
        if (!chapters.isEmpty()) {
            profile.setCurrentChapter(chapters.get(chapterIndex));
        }
    }

    private static void applyProgress(List<Chapter> chapters, int lastChapter, int lastLevel) {
        for (int c = 0; c < chapters.size(); c++) {
            Chapter chapter = chapters.get(c);
            boolean chapterReached = (c + 1) <= lastChapter;
            chapter.setUnlocked(chapterReached);
            if (!chapterReached) {
                continue;
            }
            Level[] levels = chapter.getLevels();
            for (int l = 0; l < levels.length; l++) {
                // Past chapters are fully unlocked; the current chapter unlocks up to lastLevel.
                boolean unlock = (c + 1) < lastChapter || (l + 1) <= lastLevel;
                levels[l].setUnlocked(unlock);
                // Anything strictly before the progress pointer has been cleared. Without this a
                // rebuilt campaign would report every level as uncompleted (Chapter.isComplete()
                // would never be true), since only lastChapter/lastLevel survive a save.
                levels[l].setCompleted((c + 1) < lastChapter || (l + 1) < lastLevel);
            }
        }
    }

    // Chapter templates are grouped by this key; a template with no chapter would otherwise land under
    // a null key and produce a Chapter whose getName() NPEs in EnterChapterCommand.
    private static String chapterKeyOf(LevelTemplate template) {
        String chapter = template.getChapter();
        return chapter == null || chapter.isBlank() ? EnvironmentType.ANCIENT_EGYPT.name() : chapter;
    }

    private static EnvironmentType environmentOf(String chapter) {
        if (chapter == null) {
            return EnvironmentType.ANCIENT_EGYPT;
        }
        try {
            return EnvironmentType.valueOf(chapter.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return EnvironmentType.ANCIENT_EGYPT;
        }
    }
}
