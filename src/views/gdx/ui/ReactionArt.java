package views.gdx.ui;

import models.social.Reaction;
import models.social.ReactionKind;

// What each reaction looks like.
//
// Kept out of models.social.Reaction on purpose: the catalogue is shared with the server and the
// terminal build, and neither of them has any business holding a texture id.
//
// ## Why the emojis are pictures and not characters
//
// The pvz2 skin ships six TTFs and every one of them is a GAME font -- letters, numbers and
// punctuation, drawn to match the logo. None carries an emoji glyph, so a label containing one renders
// as a tofu box. The three "emojis" are therefore sprite regions out of the shipped dump, which also
// keeps every player-facing string in this project ASCII, as the rest of it already is.
//
// Each of the three ids below is already drawn somewhere else in this build and is therefore known to
// resolve, which is not a small thing in a dump where a name describes the EFFECT a part belongs to
// rather than what it is a picture of -- see IZombieRenderer, where the region called "brain" turned
// out to be a splatter particle.
public final class ReactionArt {

    private ReactionArt() { }

    // The sun off the HUD counter: "nice one".
    private static final String SUNNY = UiArt.SUN;
    // The brain the I, Zombie lanes are played for. Verified art, and the single most on-theme thing
    // one player can wave at another in this mode.
    private static final String BRAINZ =
            "IMAGE_ZOMBIE_POWER_BRAIN_PROJECTILE_POWER_BRAIN_PROJECTILE_112X82";
    // The zombie head that rides the wave meter: "grr".
    private static final String GRR = UiArt.METER_HEAD;

    // The sticker animations. Names of PAM animations the sprite layer can load -- see ReactionPopup,
    // which draws them through EntitySprite and falls back to a still frame if one is unavailable.
    private static final String TAUNT_ANIM = "ZombieImp";
    private static final String CHEER_ANIM = "Sunflower";
    private static final String SULK_ANIM = "ZombieDefault";

    // The region id for an EMOJI reaction, or null for the other two kinds.
    public static String region(Reaction reaction) {
        if (reaction == null || reaction.kind() != ReactionKind.EMOJI) {
            return null;
        }
        return switch (reaction) {
            case SUNNY -> SUNNY;
            case BRAINZ -> BRAINZ;
            case GRR -> GRR;
            default -> null;
        };
    }

    // The animation a STICKER reaction plays, or null for the other two kinds.
    public static String animation(Reaction reaction) {
        if (reaction == null || reaction.kind() != ReactionKind.STICKER) {
            return null;
        }
        return switch (reaction) {
            case TAUNT -> TAUNT_ANIM;
            case CHEER -> CHEER_ANIM;
            case SULK -> SULK_ANIM;
            default -> null;
        };
    }

    // A short word for the button, under the picture. Not the reaction itself -- it is a label on a
    // control, and a grid of nine unlabelled images is a guessing game.
    public static String caption(Reaction reaction) {
        if (reaction == null) {
            return "";
        }
        if (reaction.kind() == ReactionKind.TEXT) {
            return reaction.text();
        }
        return switch (reaction) {
            case SUNNY -> "Nice";
            case BRAINZ -> "Brainz";
            case GRR -> "Grr";
            case TAUNT -> "Taunt";
            case CHEER -> "Cheer";
            case SULK -> "Sulk";
            default -> "";
        };
    }
}
