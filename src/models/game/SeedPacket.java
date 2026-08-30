package models.game;

import utils.Constants;

public class SeedPacket {
    private String plantType;
    private int cooldownDuration;
    private long lastPlantedTick;
    private boolean isBoosted;

    // Whether this packet is an Imitater wearing another plant's coat.
    //
    // The Imitater plants nothing of its own -- picking it is picking a SECOND packet of something you
    // already brought, with its own recharge. So the copy carries the copied plant's name, because that
    // is what it plants and what it costs, and this flag is the only thing that distinguishes it: it is
    // what lets two packets of one plant sit on the bar without one of them looking like a mistake, and
    // what tells "remove the Imitater" apart from "remove the Peashooter".
    private final boolean imitated;

    public SeedPacket(String plantType, int cooldownDuration) {
        this(plantType, cooldownDuration, false);
    }

    public SeedPacket(String plantType, int cooldownDuration, boolean imitated) {
        this.plantType = plantType;
        this.cooldownDuration = cooldownDuration;
        this.lastPlantedTick = -1;
        this.isBoosted = false;
        this.imitated = imitated;
    }

    public boolean isImitated() {
        return imitated;
    }

    public boolean isBoosted() {
        return isBoosted;
    }

    public void setBoosted(boolean boosted) {
        isBoosted = boosted;
    }

    public boolean isReady(long currentTick){
        if (mirroredRemainingTicks != null) {
            return mirroredRemainingTicks <= 0;
        }
        if (lastPlantedTick < 0){
            return true;
        }
        return currentTick - lastPlantedTick >= (long) cooldownDuration * Constants.TICKS_PER_SECOND;
    }
    public void updateLastPlantedTick(long currentTick){
        this.lastPlantedTick = currentTick;
    }
    public double getRemainingCooldownSeconds(long currentTick) {
        if (mirroredRemainingTicks != null) {
            return Math.max(0, mirroredRemainingTicks) / (double) Constants.TICKS_PER_SECOND;
        }
        if (isReady(currentTick)) {
            return 0;
        }
        long totalCooldownTicks = (long) cooldownDuration * Constants.TICKS_PER_SECOND;
        long remainingTicks = totalCooldownTicks - (currentTick - lastPlantedTick);
        return remainingTicks / (double) Constants.TICKS_PER_SECOND;
    }

    // Told, rather than measured, on a networked client's mirrored board.
    //
    // Both answers above are derived from lastPlantedTick, and lastPlantedTick is only ever written by
    // GameSession.plant(). On a mirror that method never runs -- the plant command goes to the server
    // and comes back as a board -- so the field sits at -1 and every card reports itself ready for the
    // whole match. The recharge wipe never darkens, and the card lets the player arm a packet the
    // server is about to refuse.
    //
    // A remaining count rather than a lastPlantedTick, because the mirror's own clock does not advance
    // either: a tick number would need a second frozen number to be measured against. Boxed, so null is
    // "nobody has told me" and an authoritative board keeps measuring for itself -- the same
    // distinction Profile.volume draws, and for the same reason: zero is a real answer here.
    private Integer mirroredRemainingTicks;

    public void mirrorRemainingTicks(int remainingTicks) {
        this.mirroredRemainingTicks = Math.max(0, remainingTicks);
    }
    public int getCooldownDuration() {
        return cooldownDuration;
    }
    public String getPlantType() {
        return plantType;
    }
}
