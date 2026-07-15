package models.entities.plants;

public enum PlantTags {
    DAY , NIGHT , SHROOM , WRAMP_UP , PEA , ICE , FIRE ,
    STACK , CHARGE , MAGIC , POISON , WATER , A_O_E ,
    TRAP , MOVE_ZOMBIES , SUN , EXPLOSIVE;

    // Maps a raw tag string from data/plants.json onto its enum constant, absorbing the spelling
    // differences between the data file and this enum. Returns null for unknown tags.
    public static PlantTags fromJson(String raw) {
        if (raw == null) {
            return null;
        }
        switch (raw.trim().toUpperCase()) {
            case "DAY": return DAY;
            case "NIGHT": return NIGHT;
            case "SHROOM": return SHROOM;
            case "WARM_UP": case "WRAMP_UP": return WRAMP_UP;
            case "PEA": return PEA;
            case "ICE": return ICE;
            case "FIRE": return FIRE;
            case "STACK": return STACK;
            case "CHARGE": return CHARGE;
            case "MAGIC": return MAGIC;
            case "POISON": return POISON;
            case "WATER": return WATER;
            case "AOE": case "A_O_E": return A_O_E;
            case "TRAP": return TRAP;
            case "MOVE_ZOMBIE": case "MOVE_ZOMBIES": return MOVE_ZOMBIES;
            case "SUN": return SUN;
            case "EXPLOSIVE": return EXPLOSIVE;
            default: return null;
        }
    }
}
