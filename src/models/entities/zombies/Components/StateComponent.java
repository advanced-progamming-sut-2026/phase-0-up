package models.entities.zombies.Components;

public class StateComponent {
    private ActionState currentAction = ActionState.WALKING;
    private boolean isReadyForLaser = false;
    private boolean isFlying = false;
    private boolean isSubmerged = false;
    private boolean isDecapitated = false;
    private boolean isHypnotized = false;
    private int frozenTimer = 0;
    private int chilledTimer = 0;
    private int butteredTimer = 0;
    private boolean isTorchLit = false;
    private boolean isSpinning = false;
    private boolean isImmuneToFire = false;

    private boolean isPermanentlyFrozen = false;
    private boolean freezeImmune = false;

    public void update() {
        if (frozenTimer > 0 &&!isPermanentlyFrozen) frozenTimer--;
        if (chilledTimer > 0) chilledTimer--;
        if (butteredTimer > 0) butteredTimer--;
    }

    // An ice attack (Snow Pea, Hunter's ice, a Jester-reflected ice shot) freezes a zombie solid --
    // unless it is freeze-immune. Every zombie in Frostbite Caves is immune: the spec says they do not
    // freeze when hit by ice. This guards the timed freeze from attacks; a FrostbiteTerrain block that
    // pre-freezes a zombie still uses setFrozen and is unaffected.
    public void applyFreeze(int durationInTicks) {
        if (freezeImmune) {
            return;
        }
        this.frozenTimer = durationInTicks;
    }

    public boolean isFreezeImmune() { return freezeImmune; }
    public void setFreezeImmune(boolean immune) { this.freezeImmune = immune; }

    // A chill halves a zombie's pace. Refused for the same zombies a freeze is refused for.
    //
    // The flag used to gate only applyFreeze, which left Frostbite Caves half-implemented: its zombies
    // shrugged off being frozen solid but were still slowed by every ice shot that hit them. The rule
    // the world is built on is that ICE DOES NOT AFFECT THEM -- they live in it -- so both halves of
    // what an ice attack does are refused here.
    //
    // Damage is untouched: a Snow Pea still hurts, it simply does not slow.
    public void applyChill(int durationInTicks) {
        if (freezeImmune) {
            return;
        }
        this.chilledTimer = durationInTicks;
    }

    // Snow Pea's CHILL_DURATION_EXT upgrade lengthens the chill already applied by the ice hit. Gated
    // too, or the upgrade would put a chill on an immune zombie that the hit itself could not.
    public void extendChill(int extraTicks) {
        if (freezeImmune) {
            return;
        }
        this.chilledTimer += extraTicks;
    }

    public void applyButter(int durationInTicks) {
        this.butteredTimer = durationInTicks;
    }
    public ActionState getCurrentAction() { return currentAction; }
    public void setAction(ActionState action) { this.currentAction = action; }

    public boolean isUnableToMove() {
        return isFrozen() || isButtered() || currentAction == ActionState.EATING || currentAction == ActionState.DYING;
    }

    public boolean isChilled() { return chilledTimer > 0 && !isFrozen(); }
    public boolean isButtered() { return butteredTimer > 0; }

    public boolean isFlying() { return isFlying; }
    public void setFlying(boolean flying) { isFlying = flying; }

    public boolean isSubmerged() { return isSubmerged; }
    public void setSubmerged(boolean submerged) { isSubmerged = submerged; }

    public boolean isDecapitated() { return isDecapitated; }
    public void setDecapitated(boolean decapitated) { isDecapitated = decapitated; }

    public boolean isHypnotized() { return isHypnotized; }
    public void setHypnotized(boolean hypnotized) { isHypnotized = hypnotized; }

    public void setFrozen(boolean frozen) {
        this.isPermanentlyFrozen = frozen;
        if (!frozen) {
            this.frozenTimer = 0;
        }
    }

    public boolean isFrozen() {
        return isPermanentlyFrozen || frozenTimer > 0;
    }


    public boolean isTorchLit() { return isTorchLit; }
    public void setTorchLit(boolean torchLit) { this.isTorchLit = torchLit; }
    public boolean isReadyForLaser() {
        return isReadyForLaser;
    }

    public void setReadyForLaser(boolean readyForLaser) {
        this.isReadyForLaser = readyForLaser;
    }

    public void setFrozenTimer(int v) {
        this.frozenTimer = v;
    }

    public void setChilledTimer(int i) {
        this.chilledTimer = i;
    }

    // Remaining ticks on each timed status effect, for the "zombies info" status readout. A value of 0
    // means the effect is not active. (isFrozen() may still be true with a 0 timer when the zombie is
    // permanently frozen by frostbite terrain rather than by a timed ice hit.)
    public int getFrozenTimer() { return frozenTimer; }
    public int getChilledTimer() { return chilledTimer; }
    public int getButteredTimer() { return butteredTimer; }
    public boolean isPermanentlyFrozen() { return isPermanentlyFrozen; }

    public boolean isSpinning() { return isSpinning; }
    public void setSpinning(boolean spinning) { isSpinning = spinning; }

    // Charging rather than walking -- the All-Star's opening sprint, until it hits something.
    //
    // A state, not an event: it is what the zombie is doing for that whole stretch and it is readable
    // on any frame, which is what separates it from the tackle at the end (an instant, announced as a
    // sentence and played by ZombieActions). The view draws the `run` clip while this holds.
    private boolean isRushing = false;

    public boolean isRushing() { return isRushing; }
    public void setRushing(boolean rushing) { isRushing = rushing; }

    // Drawing sun in -- the Turquoise draining the player's bank, and Ra reeling a sun off the lawn.
    //
    // A state for the same reason the All-Star's charge is one: it lasts for a stretch and is readable
    // on any frame. The view holds the `power` clip while it is true; the `power_up` and `power_down`
    // bookends either side of it are instants, announced as sentences and played by ZombieActions.
    private boolean isSiphoning = false;

    public boolean isSiphoning() { return isSiphoning; }
    public void setSiphoning(boolean siphoning) { isSiphoning = siphoning; }

    // In the air, and how far through the flight it is (0 at the launch, 1 at the landing).
    //
    // The Prospector's dynamite used to TELEPORT it: one tick on its own tile, the next on column 0,
    // with no moment in between for anything to be drawn at. A real flight needs the model to hold
    // that moment, because it is the model that owns where a zombie is -- and the progress has to be
    // readable too, since the view arcs the zombie through it and nothing else knows how far along it
    // has got.
    private boolean isAirborne = false;
    private float flightProgress;

    public boolean isAirborne() { return isAirborne; }
    public void setAirborne(boolean airborne) { isAirborne = airborne; }
    public float getFlightProgress() { return flightProgress; }
    public void setFlightProgress(float progress) { flightProgress = progress; }

    // Mid-laser: the Turquoise has stopped to line its beam up and is about to let it off.
    private boolean isFiringLaser = false;

    public boolean isFiringLaser() { return isFiringLaser; }
    public void setFiringLaser(boolean firing) { isFiringLaser = firing; }

    // Standing still to do something, as opposed to being HELD still by something.
    //
    // Deliberately separate from isUnableToMove(). That one is the "can this zombie act at all" gate --
    // frozen, buttered, mid-bite -- and most abilities return early on it, including the two this
    // covers. Folding channelling into it would stop the very ability doing the channelling: a
    // Turquoise would freeze on its first siphon tick and never finish the heist or fire the beam.
    //
    // So this asks a narrower question, and only MovementComponent asks it: the zombie is rooted while
    // it draws sun in and while it aims, and walks the rest of the time.
    public boolean isRooted() {
        return isSiphoning || isFiringLaser;
    }
    public boolean isImmuneToFire() { return isImmuneToFire; }
    public void setImmuneToFire(boolean immuneToFire) { isImmuneToFire = immuneToFire; }

    // Whether this zombie's fire immunity still needs announcing. True exactly once, for the first fire
    // shot it swallows.
    //
    // A fire-proof zombie is invisible as a RULE: the shot arrives, it bursts like any other, and the
    // health bar does not move -- which reads as a broken plant rather than as a zombie in a dragon
    // suit. It has to be said. But a Fire Peashooter fires about once a second and a pair of them will
    // stand there emptying into an Imp Dragon for a minute, so saying it every time is a wall of
    // identical lines in the terminal build and a stack of identical toasts in the GUI. Once per zombie
    // is the whole message.
    private boolean fireImmunityAnnounced;

    public boolean shouldAnnounceFireImmunity() {
        if (fireImmunityAnnounced) {
            return false;
        }
        fireImmunityAnnounced = true;
        return true;
    }
}
