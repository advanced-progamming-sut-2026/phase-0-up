package models.entities.zombies.Components;

public enum ArmorType {
    // The third flag marks a "front shield": something carried IN FRONT of the zombie rather than worn
    // on it. A lobbed shot arcs over those and lands on what is behind them, so they protect only
    // against straight-line fire (see HealthComponent.applyDamage). Head and body armor -- cone,
    // bucket, crown, shoulder plate -- is worn, so an overhead melon still has to chew through it.
    BASE_BODY(190 , false, false),
    CONE(370 , false, false),
    BUCKET(1100 , true, false),
    BRICK(2200 , false, false),
    CROWN(1600 , true, false),
    NEWSPAPER(800 , false, true),        // held up in front of the zombie
    SHOULDER_ARMOR(1600 , true, false),
    ICE_BLOCK(300 , false, false),       // encases the zombie, so it is not "in front" of anything
    BARREL(190 , false, true);           // shoved along ahead of the zombie
    private int hp;
    private boolean metallic;
    private final boolean frontShield;

    ArmorType(int hp, boolean metallic, boolean frontShield) {
        this.hp = hp;
        this.metallic = metallic;
        this.frontShield = frontShield;
    }

    public int getHp() {
        return hp;
    }

    public boolean isMetallic() {
        return metallic;
    }

    // True when this layer is carried in front and can therefore be flown over by a lobbed shot.
    public boolean isFrontShield() {
        return frontShield;
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
