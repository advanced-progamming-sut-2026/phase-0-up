package controllers.systems.game;

import factories.QuestFactory;
import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import models.map.Cell;
import models.map.Row;
import models.quests.Quest;
import models.quests.QuestContext;
import models.templates.QuestTemplate;
import models.user.Profile;
import utils.Constants;
import utils.Result;
import utils.registry.QuestRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// Owns the quest side of the game: it tallies the per-level facts a quest condition cares about, and
// it is the sorting engine behind the travel log.
//
// The travel log shows quests ranked by priority so the most important always sit at the top, exactly
// as the spec asks: Critical (story / plant-unlock quests) first, then High (Epic gem challenges),
// then Medium and Low (daily, repeatable quests). Within a tier the authored order is kept.
public class QuestSystem {

    // --- Per-level tally (the raw facts a quest condition is evaluated against) ------------------
    // Fed live during play: CollectSunCommand reports sun, CombatSystem reports each kill / plant loss
    // / mower kill as it happens. So completion reads the running tally, not an end-of-level snapshot.
    private int sunCollectedThisLevel;
    private int zombiesKilledThisLevel;
    private int plantsLostThisLevel;
    private int lawnmowerKillsThisLevel;
    private int mowerlessFirstColumnKillsThisLevel;
    private final java.util.Map<String, Integer> killsByPlant = new java.util.HashMap<>();
    private final java.util.Map<String, Integer> killsByFamily = new java.util.HashMap<>();

    public void startTrackingLevel(GameSession session) {
        sunCollectedThisLevel = 0;
        zombiesKilledThisLevel = 0;
        plantsLostThisLevel = 0;
        lawnmowerKillsThisLevel = 0;
        mowerlessFirstColumnKillsThisLevel = 0;
        killsByPlant.clear();
        killsByFamily.clear();
    }

    public void recordSunCollected(int amount) {
        if (amount > 0) {
            sunCollectedThisLevel += amount;
        }
    }

    // Notified by CombatSystem the moment a zombie dies. Besides the running count, the killer plant
    // (when there is one) is tallied by name, which drives the "kill only with plant X" quests.
    public void recordZombieKilled(Zombie zombie, Plant killer) {
        zombiesKilledThisLevel++;
        if (killer != null && killer.getName() != null) {
            killsByPlant.merge(killer.getName().toLowerCase().trim(), 1, Integer::sum);
        }
        // The killer's family (its plant category) drives the Family Massacre quest. Environmental
        // kills (mower, nuke) have no killer plant and so are credited to no family.
        if (killer != null && killer.getCategory() != null && !killer.getCategory().isBlank()) {
            killsByFamily.merge(killer.getCategory().toLowerCase().trim(), 1, Integer::sum);
        }
    }

    // A plant kill landed in column 0 of a row whose mower is already spent (Almost Victorious). The
    // caller (CombatSystem) owns the "which column / is the mower gone" test, since it has the board.
    public void recordMowerlessFirstColumnKill() {
        mowerlessFirstColumnKillsThisLevel++;
    }

    public void recordPlantLost() {
        plantsLostThisLevel++;
    }

    public void recordLawnmowerKills(int count) {
        if (count > 0) {
            lawnmowerKillsThisLevel += count;
        }
    }

    public int getSunCollectedThisLevel() { return sunCollectedThisLevel; }
    public int getZombiesKilledThisLevel() { return zombiesKilledThisLevel; }
    public int getPlantsLostThisLevel() { return plantsLostThisLevel; }
    public int getLawnmowerKillsThisLevel() { return lawnmowerKillsThisLevel; }
    public java.util.Map<String, Integer> getKillsByPlantThisLevel() {
        return new java.util.HashMap<>(killsByPlant);
    }

    // --- Completion (evaluated once, when a level ends) ------------------------------------------

    // Snapshots the finished level into a QuestContext: sun banked over the level, sun left in the
    // bank, kills, plants lost, mower kills, and the final garden layout.
    private QuestContext buildContext(GameSession session, boolean won) {
        int rows = session.getMap().getRows().size();
        int cols = Constants.BOARD_COLS;
        boolean[][] grid = new boolean[rows][cols];
        for (int r = 0; r < rows; r++) {
            Row row = session.getMap().getRow(r);
            for (int c = 0; c < cols && c < row.getCells().size(); c++) {
                Cell cell = row.cellAt(c);
                grid[r][c] = cell.hasPlant() || cell.hasProtector() || cell.hasPlatform();
            }
        }
        Profile profile = session.getPlayer();
        // Kills, plants lost, mower kills and kills-by-plant/family come from this system's own live
        // tally (fed by CombatSystem); sun left, the garden layout, the plantings and the first-30s
        // kill count are read off the finished session; the win streak and per-chapter kill total are
        // read off the profile, having just been updated for this level by updatePersistentProgress.
        return QuestContext.builder()
                .won(won)
                .sunCollected(sunCollectedThisLevel)
                .finalSun(session.getSunAmount())
                .zombiesKilled(zombiesKilledThisLevel)
                .plantsLost(plantsLostThisLevel)
                .lawnmowerKills(lawnmowerKillsThisLevel)
                .killsInFirst30s(session.getKillsInFirst30s())
                .mowerlessFirstColumnKills(mowerlessFirstColumnKillsThisLevel)
                .winStreakAtMaxDifficulty(profile == null ? 0 : profile.getWinStreakAtMaxDifficulty())
                .chapterZombiesKilled(profile == null ? 0 : profile.getChapterZombieKills(chapterOf(session)))
                .killsByPlant(new java.util.HashMap<>(killsByPlant))
                .killsByFamily(new java.util.HashMap<>(killsByFamily))
                .plantedCategories(new java.util.ArrayList<>(session.getPlantedCategories()))
                .plantedNames(new java.util.ArrayList<>(session.getPlantedNames()))
                .plantGrid(grid)
                .build();
    }

    // Folds this finished level into the profile's persistent, cross-level quest counters before the
    // quests are evaluated: the max-difficulty win streak (Win After Win) and the running per-chapter
    // kill total (Chapter Hunter). Called exactly once per level end, for a win or a loss.
    private void updatePersistentProgress(Profile profile, GameSession session, boolean won) {
        if (profile == null) {
            return;
        }
        boolean atMaxDifficulty = profile.getDifficultyLevel() >= Constants.MAX_DIFFICULTY_LEVEL;
        profile.recordLevelForWinStreak(won, atMaxDifficulty);
        String chapter = chapterOf(session);
        if (chapter != null) {
            profile.addChapterZombieKills(chapter, zombiesKilledThisLevel);
        }
    }

    // The chapter the finished level belongs to (its authored "chapter" tag), or null if unknown.
    private String chapterOf(GameSession session) {
        if (session != null && session.getLevel() != null && session.getLevel().getTemplate() != null) {
            return session.getLevel().getTemplate().getChapter();
        }
        return null;
    }

    // Evaluates every quest against the finished level. Called once when a level ends, for a win or a
    // loss: the cross-level counters (win streak, chapter kills) are updated first, then every quest is
    // tested against the resulting context. A quest that is newly satisfied (and not already completed
    // on the profile) has its reward granted once, is recorded on the profile, and is announced.
    // Returns those announcements for the caller to render.
    public List<Result> evaluateAndComplete(Profile profile, GameSession session, boolean won) {
        List<Result> events = new ArrayList<>();
        if (profile == null || session == null) {
            return events;
        }
        updatePersistentProgress(profile, session, won);
        QuestContext ctx = buildContext(session, won);
        for (QuestTemplate template : QuestRegistry.getInstance().getAllQuestTemplates()) {
            Quest quest = QuestFactory.createQuest(template);
            if (quest == null || profile.hasCompletedQuest(quest.getId()) || !quest.isSatisfiedBy(ctx)) {
                continue;
            }
            quest.getReward().grant(profile);
            profile.markQuestCompleted(quest.getId());
            if (quest.getCategory() == Quest.Category.DAILY) {
                profile.incrementDailyQuestsDone();
            } else {
                profile.incrementNoneDailyQuestsDone();
            }
            events.add(new Result(true, "Quest complete: " + quest.getName()
                    + "! Reward: " + quest.getReward().describe() + "."));
        }
        return events;
    }

    // --- Sorting engine (travel log) -------------------------------------------------------------

    // Every quest, freshly built from the registry and ranked by priority. QuestPriority is declared
    // CRITICAL, HIGH, MEDIUM, LOW, so ordering by its ordinal gives the required top-to-bottom order;
    // the sort is stable, so quests of equal priority keep their authored order.
    public List<Quest> getSortedQuestsForLog() {
        List<Quest> quests = new ArrayList<>();
        for (QuestTemplate template : QuestRegistry.getInstance().getAllQuestTemplates()) {
            Quest quest = QuestFactory.createQuest(template);
            if (quest != null) {
                quests.add(quest);
            }
        }
        quests.sort(Comparator.comparingInt(q -> q.getPriority().ordinal()));
        return quests;
    }

    // The quests on one travel-log page (one quest category), still ranked by priority. A null category
    // returns the whole sorted list.
    public List<Quest> getQuestsForPage(Quest.Category category) {
        List<Quest> sorted = getSortedQuestsForLog();
        if (category == null) {
            return sorted;
        }
        List<Quest> page = new ArrayList<>();
        for (Quest quest : sorted) {
            if (quest.getCategory() == category) {
                page.add(quest);
            }
        }
        return page;
    }

    // Same as above but flags each quest complete from the profile's record, so the travel log shows
    // which quests the player has already finished.
    public List<Quest> getQuestsForPage(Quest.Category category, Profile profile) {
        return withCompletion(getQuestsForPage(category), profile);
    }

    public List<Quest> getSortedQuestsForLog(Profile profile) {
        return withCompletion(getSortedQuestsForLog(), profile);
    }

    private List<Quest> withCompletion(List<Quest> quests, Profile profile) {
        if (profile != null) {
            for (Quest quest : quests) {
                if (profile.hasCompletedQuest(quest.getId())) {
                    quest.markComplete();
                }
            }
        }
        return quests;
    }
}
