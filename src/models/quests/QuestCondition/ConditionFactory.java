package models.quests.QuestCondition;

import models.templates.QuestTemplate.ConditionSpec;

// Builds the completion condition for a quest from its authored spec. The single place that maps a
// condition type onto a concrete QuestCondition, so nothing else switches on the type. An unknown or
// absent type yields a NoCondition (the quest stays open rather than mis-completing).
public final class ConditionFactory {
    private ConditionFactory() { }

    // Split by what the condition reads so each half stays inside the 50-line method limit: the
    // counting conditions (a tally reaching a threshold) first, then the ones that test the shape of
    // the final garden. An unknown type falls through both to NoCondition.
    public static QuestCondition create(ConditionSpec spec) {
        if (spec == null || spec.getType() == null) {
            return new NoCondition();
        }
        String type = spec.getType().toUpperCase();
        QuestCondition counting = createCountingCondition(type, spec);
        return counting != null ? counting : createLayoutCondition(type, spec);
    }

    // Conditions satisfied by a running total: sun banked, zombies killed, plants placed.
    private static QuestCondition createCountingCondition(String type, ConditionSpec spec) {
        switch (type) {
            case "COLLECT_SUN":
                return new CollectSunCondition(spec.getThreshold());
            case "MAX_PLANTS_LOST":
                return new MaxPlantsLostCondition(spec.getThreshold());
            case "KILL_COUNT":
                return new KillCountCondition(spec.getThreshold());
            case "KILL_WITH_PLANT":
                return new KillWithPlantCondition(spec.getPlant(), spec.getThreshold());
            case "KILL_WITH_SINGLE_PLANT":
                return new KillWithSinglePlantCondition(spec.getThreshold());
            case "LAWNMOWER_KILLS":
                return new LawnmowerKillsCondition(spec.getThreshold());
            case "PLANT_CATEGORY_COUNT":
                return new PlantCategoryCountCondition(spec.getCategory(), spec.getThreshold());
            case "ONLY_CATEGORY":
                return new OnlyCategoryCondition(spec.getCategory(), spec.getThreshold());
            case "KILLS_WITHIN_WINDOW":
                return new KillsWithinWindowCondition(spec.getThreshold());
            case "WIN_STREAK":
                return new WinStreakCondition(spec.getThreshold());
            case "MOWERLESS_FIRST_COLUMN":
                return new MowerlessFirstColumnCondition(spec.getThreshold());
            case "CHAPTER_KILL_COUNT":
                return new ChapterKillCountCondition(spec.getThreshold());
            default:
                return null;   // not a counting condition -- let the layout group try
        }
    }

    // Conditions satisfied by the state the level ended in: the garden's shape, the plant families
    // used, the sun left unspent.
    private static QuestCondition createLayoutCondition(String type, ConditionSpec spec) {
        switch (type) {
            case "ZERO_SUN":
                return new ZeroSunCondition();
            case "EMPTY_ROW":
                return new EmptyRowCondition(spec.getIndex());
            case "EMPTY_COLUMN":
                return new EmptyColumnCondition(spec.getIndex());
            case "SYMMETRIC":
                return new SymmetryCondition(true);
            case "ASYMMETRIC":
                return new SymmetryCondition(false);
            case "EMPTY_CROSS":
                return new EmptyCrossCondition(spec.getIndex());
            case "ONLY_MUSHROOMS":
                return new OnlyMushroomsCondition();
            case "ONLY_FAMILY":
                return new FamilyMassacreCondition();
            case "WITHOUT_FAMILY":
                return new WithoutFamilyCondition(spec.getFamily());
            default:
                return new NoCondition();
        }
    }
}
