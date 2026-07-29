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
    // Fed live during play: CollectSunCommand reports sun and CombatSystem reports each kill as it
    // happens, so completion reads a running tally rather than an end-of-level snapshot.
    //
    // Only the facts nothing else already counts are kept here. Zombies killed, plants lost and mower
    // kills live on the GameSession, which every death path in the game already updates, and are read
    // straight off it at level end -- a second parallel tally here would silently drift the moment one
    // of those paths forgot to notify this system, which is exactly how the Fisherman Zombie's drowned
    // plants went uncounted against Economical Herbivore.
    private int mowerlessFirstColumnKillsThisLevel;
    private int sunCollectedThisLevel;
    private final java.util.Map<String, Integer> killsByPlant = new java.util.HashMap<>();
    private final java.util.Map<String, Integer> killsByFamily = new java.util.HashMap<>();
    // The profile playing this level, captured at kick-off so live events (sun pickups) can be credited
    // to its cross-level, cross-day counters the moment they happen.
    private Profile trackedProfile;
    // Whether this level has already been folded into the profile's running totals. See
    // updatePersistentProgress -- crediting one level twice would inflate every cumulative quest.
    private boolean settled;

    public void startTrackingLevel(GameSession session) {
        mowerlessFirstColumnKillsThisLevel = 0;
        sunCollectedThisLevel = 0;
        settled = false;
        killsByPlant.clear();
        killsByFamily.clear();
        trackedProfile = session == null ? null : session.getPlayer();
    }

    // A sun token was picked up. Beyond this level, it is banked into the player's running total for
    // the current calendar day -- the figure the Daily Sun Catcher is judged against, which keeps
    // accumulating across levels and resets only when the date changes.
    public void recordSunCollected(int amount) {
        if (amount <= 0) {
            return;
        }
        sunCollectedThisLevel += amount;
        if (trackedProfile != null) {
            trackedProfile.addSunCollectedToday(amount);
        }
    }

    // Notified by CombatSystem the moment a zombie dies. The overall kill count is the GameSession's
    // job; what is tallied here is the killer plant (when there is one), by name and by family, which
    // drives the "kill only with plant X" and "kill only with family Y" quests.
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
        // Kills-by-plant/family and the mowerless last-stand kills come from this system's own live
        // tally (fed by CombatSystem); the kill/plant-loss/mower totals, sun left, garden layout,
        // plantings and first-30s kills are read off the finished session, which is the one counter
        // every gameplay path already updates; the day's sun, the win streak and the per-chapter kill
        // total are read off the profile, which carries them between levels.
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
                .plantedCategories(new java.util.ArrayList<>(session.getPlantedCategories()))
                .plantedNames(new java.util.ArrayList<>(session.getPlantedNames()))
                .plantGrid(grid)
                .build();
    }

    // Folds this finished level into the profile's persistent, cross-level quest counters before the
    // quests are evaluated: the max-difficulty win streak (Win After Win), the per-chapter kill total
    // (Chapter Hunter), the day's last-ditch kills (Almost Victorious), and the lifetime mower kills
    // (Mowing Time). Pro Demolisher is NOT here: the spreadsheet scopes it to a single level.
    //
    // Runs exactly once per level end, for a win or a loss -- `settled` is the guard. These are running
    // totals, so evaluating the same finished level twice would credit it twice and let a quest complete
    // on kills the player never made.
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
    }

    // Whether this level is a "day" level, i.e. its season drops sun from the sky. Every season does
    // except Dark Ages, which is the game's night setting -- so this is the test Night or Morning uses
    // to insist the mushroom run happened in daylight. An unknown chapter falls back to Ancient Egypt
    // (as EnvironmentType.fromChapter defines), which is a day season.
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
    // tested against the resulting context. A quest that is newly satisfied (and not already earned)
    // has its reward granted exactly once, is recorded on the profile, and is announced. Returns those
    // announcements for the caller to render.
    //
    // Anything that completed is written to the database before this returns, so a finished quest --
    // its reward and the lifetime counters the leaderboard ranks on -- is durable the instant it is
    // earned rather than only if the player later exits cleanly.
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
            // Through the quest's own claim path, so the once-only guard that Quest owns is the thing
            // that hands the reward over. Belt and braces with the profile record checked above: even
            // a duplicated call here cannot pay a player twice.
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

    // Whether this quest is already banked and must not pay out again. A daily quest is blocked only
    // for the rest of today -- it is repeatable, and that is the whole point of the category -- while
    // a main or epic quest is a one-off achievement and stays blocked forever.
    private boolean alreadyEarned(Profile profile, Quest quest) {
        return quest.getCategory() == Quest.Category.DAILY
                ? profile.hasCompletedDailyQuestToday(quest.getId())
                : profile.hasCompletedQuest(quest.getId());
    }

    // Books a freshly earned quest: the per-quest record that stops it paying out again, and the
    // lifetime tally the leaderboard's Daily / Non-Daily Quests columns are built from. The tally is
    // never reset -- it counts everything the player has ever finished, so a daily quest earned again
    // tomorrow adds to it again.
    private void recordEarned(Profile profile, Quest quest) {
        if (quest.getCategory() == Quest.Category.DAILY) {
            profile.markDailyQuestCompletedToday(quest.getId());
            profile.incrementDailyQuestsDone();
        } else {
            profile.markQuestCompleted(quest.getId());
            profile.incrementNoneDailyQuestsDone();
        }
    }

    // Flushes the profile to the save file. A failure is reported through the returned event list
    // rather than thrown, so a disk problem cannot swallow the level-end announcements the player is
    // owed -- and cannot take down the tick loop either.
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

    // Flags each quest complete from the profile's record. Category-aware for the same reason the
    // completion gate is: a daily quest shows as done only for the day it was earned, so tomorrow's
    // travel log offers it again instead of showing a page of permanently ticked-off dailies.
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
