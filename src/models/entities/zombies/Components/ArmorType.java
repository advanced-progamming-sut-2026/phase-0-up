package models.entities.zombies.Components;

public enum ArmorType {
    BASE_BODY(190 , false),
    CONE(370 , false),
    BUCKET(1100 , true),
    BRICK(2200 , false),
    CROWN(1600 , true),
    NEWSPAPER(800 , false),
    SHOULDER_ARMOR(1600 , true),
    ICE_BLOCK(300 , false),
    BARREL(190 , false);
    private int hp;
    private boolean metallic;

    ArmorType(int hp, boolean metallic) {
        this.hp = hp;
        this.metallic = metallic;
    }

    public int getHp() {
        return hp;
    }

    public boolean isMetallic() {
        return metallic;
    }

    // Human-readable armor name in camelCase for status readouts: SHOULDER_ARMOR -> "shoulderArmor",
    // CONE -> "cone", ICE_BLOCK -> "iceBlock". Derived from the enum name so a new armor type gets a
    // sensible label automatically.
    public String getDisplayName() {
        String[] parts = name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (parts[i].isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        }
        return sb.toString();
    }
}
