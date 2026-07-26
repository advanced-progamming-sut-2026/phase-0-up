package models.quests;

// How far along a quest is: where the player stands against the number the quest asks for.
//
// crossLevel separates the two kinds of quest the game has, because they mean different things to a
// player reading the travel log. A cross-level quest (chapter kill totals, the max-difficulty win
// streak) accumulates on the profile and survives between matches, so "12/20" is a running tally that
// carries forward. A single-level quest has to be achieved inside one match, so its stored progress is
// always 0 until the level ends and the whole thing either completes or does not -- reporting a
// running number there would promise a carry-over that does not exist.
public record QuestProgress(int current, int target, boolean crossLevel) {

    // A quest whose goal is not a countable number (empty row, symmetry, no-sun) has nothing to show.
    public static QuestProgress none() {
        return null;
    }

    public static QuestProgress crossLevel(int current, int target) {
        return new QuestProgress(current, target, true);
    }

    public static QuestProgress perLevel(int target) {
        return new QuestProgress(0, target, false);
    }

    // The completed version of this goal, for a quest the profile has already recorded as done.
    public QuestProgress completed() {
        return new QuestProgress(target, target, crossLevel);
    }

    public boolean isMeasurable() {
        return target > 0;
    }

    // The line shown in the travel log, or null when there is no countable goal to show.
    public String describe() {
        if (!isMeasurable()) {
            return null;
        }
        int shown = Math.min(current, target);
        if (shown >= target) {
            return "Progress: " + target + "/" + target + " -- done!";
        }
        return crossLevel
                ? "Progress: " + shown + "/" + target
                : "Progress: " + shown + "/" + target + " (must all happen in a single level)";
    }
}
