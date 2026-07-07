package models.entities.zombies.Components;

public enum ArmorType {
    BASE_BODY(190 , false),
    CONE(370 , false),
    BUCKET(1100 , true),
    BRICK(2200 , false),
    CROWN(1600 , true),
    NEWSPAPER(800 , false),
    SHOULDER_ARMOR(1600 , true);
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
}
