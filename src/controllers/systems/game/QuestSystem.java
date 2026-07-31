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
    // Only the facts nothing else counts: kills/losses/mower kills live on GameSession, not here.
    private int mowerlessFirstColumnKillsThisLevel;
    private int sunCollectedThisLevel;
    private final java.util.Map<String, Integer> killsByPlant = new java.util.HashMap<>();
    private final java.util.Map<String, Integer> killsByFamily = new java.util.HashMap<>();
    private Profile trackedProfile;
    private boolean settled;

    public void startTrackingLevel(GameSession session) {
        mowerlessFirstColumnKillsThisLevel = 0;
        sunCollectedThisLevel = 0;
        settled = false;
        killsByPlant.clear();
        killsByFamily.clear();
        trackedProfile = session == null ? null : session.getPlayer();
    }

    public void recordSunCollected(int amount) {
        if (amount <= 0) {
            return;
        }
        sunCollectedThisLevel += amount;
        if (trackedProfile != null) {
            trackedProfile.addSunCollectedToday(amount);
        }
    }

    public void recordZombieKilled(Zombie zombie, Plant killer) {
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
        return QuestContext.builder()
                .won(won)
                .sunCollected(sunCollectedThisLevel)
                .sunCollectedToday(profile == null ? 0 : profile.getSunCollectedToday())
                .dayLevel(isDayLevel(session))
                .finalSun(session.getSunAmount())
                .zombiesKilled(session.getZombiesKilled())
                .plantsLost(session.getPlantsLost())
                .lawnmowerKills(session.getLawnmowerKills())
                .killsInFirst30s(session.getKillsInFirst30s())
                .mowerlessFirstColumnKills(mowerlessFirstColumnKillsThisLevel)
                .mowerlessFirstColumnKillsToday(profile == null ? 0 : profile.getMowerlessFirstColumnKillsToday())
                .lawnmowerKillsTotal(profile == null ? 0 : profile.getLawnmowerKillsTotal())
                .winStreakAtMaxDifficulty(profile == null ? 0 : profile.getWinStreakAtMaxDifficulty())
                .chapterZombiesKilled(profile == null ? 0 : profile.getChapterZombieKills(chapterOf(session)))
                .killsByPlant(new java.util.HashMap<>(killsByPlant))
                .killsByFamily(new java.util.HashMap<>(killsByFamily))
                .killsByPlantToday(profile == null
                        ? new java.util.HashMap<>() : new java.util.HashMap<>(profile.getKillsByPlantToday()))
                .killsByChapter(profile == null
                        ? new java.util.HashMap<>() : new java.util.HashMap<>(profile.getZombieKillsByChapter()))
                .plantedCategories(new java.util.ArrayList<>(session.getPlantedCategories()))
                .plantedNames(new java.util.ArrayList<>(session.getPlantedNames()))
                .plantGrid(grid)
                .build();
    }

    // Folds this finished level into the profile's persistent, cross-level quest counters before the
    // quests are evaluated (not Pro Demolisher). Once per level end; `settled` guards double-credit.
    private void updatePersistentProgress(Profile profile, GameSession session, boolean won) {
        if (profile == null || settled) {
            return;
        }
        settled = true;
        boolean atMaxDifficulty = profile.getDifficultyLevel() >= Constants.MAX_DIFFICULTY_LEVEL;
        profile.recordLevelForWinStreak(won, atMaxDifficulty);
        String chapter = chapterOf(session);
        if (chapter != null) {
            profile.addChapterZombieKills(chapter, session.getZombiesKilled());
        }
        profile.addLawnmowerKills(session.getLawnmowerKills());
        profile.addMowerlessFirstColumnKillsToday(mowerlessFirstColumnKillsThisLevel);
        for (java.util.Map.Entry<String, Integer> entry : killsByPlant.entrySet()) {
            profile.addKillsTodayByPlant(entry.getKey(), entry.getValue());   // Pro Plant Player
        }
    }

    private boolean isDayLevel(GameSession session) {
        return models.game.EnvironmentType.fromChapter(chapterOf(session)).hasSkySunDrops();
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
    // tested against it; completions are flushed to the database so a reward survives a dirty exit.
    public List<Result> evaluateAndComplete(Profile profile, GameSession session, boolean won) {
        List<Result> events = new ArrayList<>();
        if (profile == null || session == null) {
            return events;
        }
        updatePersistentProgress(profile, session, won);
        QuestContext ctx = buildContext(session, won);
        for (QuestTemplate template : QuestRegistry.getInstance().getAllQuestTemplates()) {
            Quest quest = QuestFactory.createQuest(template);
            if (quest == null || alreadyEarned(profile, quest) || !quest.isSatisfiedBy(ctx)) {
                continue;
            }
            quest.markComplete();
            if (!quest.claim(profile)) {
                continue;
            }
            recordEarned(profile, quest);
            events.add(new Result(true, "Quest complete: " + quest.getName()
                    + "! Reward: " + quest.getReward().describe() + "."));
        }
        if (!events.isEmpty()) {
            persist(events);
        }
        return events;
    }

    private boolean alreadyEarned(Profile profile, Quest quest) {
        return quest.getCategory() == Quest.Category.DAILY
                ? profile.hasCompletedDailyQuestToday(quest.getId())
                : profile.hasCompletedQuest(quest.getId());
    }
    private void recordEarned(Profile profile, Quest quest) {
        if (quest.getCategory() == Quest.Category.DAILY) {
            profile.markDailyQuestCompletedToday(quest.getId());
            profile.incrementDailyQuestsDone();
        } else {
            profile.markQuestCompleted(quest.getId());
            profile.incrementNoneDailyQuestsDone();
        }
    }
    private void persist(List<Result> events) {
        try {
            utils.storage.DatabaseManager.getInstance().saveAll();
        } catch (RuntimeException e) {
            events.add(new Result(false,
                    "Quest progress was earned but could not be saved: " + e.getMessage()));
        }
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
                if (alreadyEarned(profile, quest)) {
                    quest.markComplete();
                }
            }
        }
        return quests;
    }
}
