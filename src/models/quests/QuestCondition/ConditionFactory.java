package models.quests.QuestCondition;

import models.templates.QuestTemplate.ConditionSpec;

// Builds the completion condition for a quest from its authored spec. The single place that maps a
// condition type onto a concrete QuestCondition, so nothing else switches on the type. An unknown or
// absent type yields a NoCondition (the quest stays open rather than mis-completing).
public final class ConditionFactory {
    private ConditionFactory() { }

    public static QuestCondition create(ConditionSpec spec) {
        if (spec == null || spec.getType() == null) {
            return new NoCondition();
        }
        switch (spec.getType().toUpperCase()) {
            case "COLLECT_SUN":
                return new CollectSunCondition(spec.getThreshold());
            case "ZERO_SUN":
                return new ZeroSunCondition();
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
            case "PLANT_CATEGORY_COUNT":
                return new PlantCategoryCountCondition(spec.getCategory(), spec.getThreshold());
            case "ONLY_CATEGORY":
                return new OnlyCategoryCondition(spec.getCategory(), spec.getThreshold());
            case "ONLY_MUSHROOMS":
                return new OnlyMushroomsCondition();
            case "KILLS_WITHIN_WINDOW":
                return new KillsWithinWindowCondition(spec.getThreshold());
            case "ONLY_FAMILY":
                return new FamilyMassacreCondition();
            case "WITHOUT_FAMILY":
                return new WithoutFamilyCondition(spec.getFamily());
            case "WIN_STREAK":
                return new WinStreakCondition(spec.getThreshold());
            case "MOWERLESS_FIRST_COLUMN":
                return new MowerlessFirstColumnCondition(spec.getThreshold());
            case "CHAPTER_KILL_COUNT":
                return new ChapterKillCountCondition(spec.getThreshold());
            default:
                return new NoCondition();
        }
    }
}
