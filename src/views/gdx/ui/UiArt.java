package views.gdx.ui;

import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import views.gdx.core.Assets;

import java.util.HashMap;
import java.util.Map;

// The game's own HUD art, by RESOURCES.json id.
//
// Everything here is a real shipped sub-image. The first pass at this screen drew flat coloured
// rectangles because none of these ids had been looked up yet -- but PvZ2's HUD is all in the dump, and
// a hand-drawn panel next to authentic plant art looks exactly as wrong as it is.
//
// Ids were found with -Dpvz.dumpParts / a RESOURCES.json search and each one confirmed to resolve with
// -Dpvz.probeRegions before being named. An id that does not resolve returns null and callers fall back
// to a plain fill, so a rename degrades the look instead of crashing the screen.
public final class UiArt {

    public static final String SUN = "IMAGE_UI_HUD_INGAME_SUN";
    public static final String PANEL = "IMAGE_UI_HUD_INGAME_BACKGROUND_3SLICE";
    // The blank seed packet the plant portraits are meant to sit on. It lives in the same
    // UI_SEEDPACKETS atlas as the portraits, which is the giveaway that the two belong together --
    // IMAGE_UI_PACKETS_* is not only plants.
    public static final String SEED_PACKET = "IMAGE_UI_PACKETS_EMPTY_PACKET";

    // Alternates on the same frame, kept for when the bank should match the world it is played in:
    // BOOST for a boosted packet, and one per world. Not wired up yet -- GameHud is not told which
    // environment the level is in, and inventing that wiring for a cosmetic swap is not worth it until
    // the seed-selection screen exists (T4.7).
    public static final String SEED_PACKET_BOOST = "IMAGE_UI_PACKETS_BOOST";
    public static final String SEED_PACKET_EGYPT = "IMAGE_UI_PACKETS_EGYPT";
    public static final String SEED_PACKET_ICEAGE = "IMAGE_UI_PACKETS_ICEAGE";
    public static final String SHOVEL_BUTTON = "IMAGE_UI_HUD_INGAME_SHOVEL_BUTTON";
    public static final String SHOVEL_ICON = "IMAGE_UI_HUD_INGAME_SHOVEL_ICON";
    public static final String PLANTFOOD_BANK = "IMAGE_UI_HUD_INGAME_PLANTFOOD_BANK";
    public static final String PLANTFOOD_SLOT = "IMAGE_UI_HUD_INGAME_PLANTFOOD_BANK_FILLED_SLOT";
    public static final String PLANTFOOD_BUTTON = "IMAGE_UI_HUD_INGAME_PLANTFOOD_BUTTON";
    public static final String METER = "IMAGE_UI_HUD_INGAME_PROGRESS_METER";
    public static final String METER_FILL = "IMAGE_UI_HUD_INGAME_PROGRESS_METER_FILL";
    public static final String METER_FLAG = "IMAGE_UI_HUD_INGAME_PROGRESS_METER_FLAG_DEFAULT";
    public static final String METER_HEAD = "IMAGE_UI_HUD_INGAME_PROGRESS_METER_ZOMBIEHEAD";
    public static final String PAUSE_BUTTON = "IMAGE_UI_HUD_INGAME_PAUSE_BUTTON";
    public static final String COIN = "IMAGE_UI_HUD_INGAME_COIN";

    // The belt Wall-nut Bowling hands its nuts out on, in place of a seed bank. Two pieces: a 253x19
    // lip the packets sit on and a 253x36 body under it. Both are authored to stretch along x, which is
    // the only direction a conveyor ever needs to.
    public static final String CONVEYOR_TOP = "IMAGE_UI_CONVEYOR_CONVEYOR_TOP";
    public static final String CONVEYOR_BELT = "IMAGE_UI_CONVEYOR_CONVEYOR_BELT";

    private final Assets assets;
    private final Map<String, TextureRegion> regions = new HashMap<>();
    private final Map<String, Drawable> drawables = new HashMap<>();

    public UiArt(Assets assets) {
        this.assets = assets;
    }

    // The pre-rendered seed packet for a plant -- the whole card, plant art and all, exactly as the
    // real game draws it. 564 of them ship in the UI_SEEDPACKETS atlas.
    //
    // The id is derived from the plant's name, but the naming is NOT uniform: most are squashed to one
    // word (SNOWPEA, WALLNUT, POTATOMINE, TWINSUNFLOWER) while a few keep a separator
    // (CHERRY_BOMB). Both forms are tried rather than special-casing the exceptions, since the roster
    // is 40-odd plants and any of them could go either way.
    public TextureRegion packet(String plantName) {
        if (plantName == null || plantName.isBlank()) {
            return null;
        }
        String upper = plantName.toUpperCase(java.util.Locale.ROOT);
        TextureRegion squashed = region("IMAGE_UI_PACKETS_" + upper.replaceAll("[^A-Z0-9]", ""));
        if (squashed != null) {
            return squashed;
        }
        return region("IMAGE_UI_PACKETS_" + upper.replaceAll("[^A-Z0-9]+", "_"));
    }

    public TextureRegion region(String id) {
        // computeIfAbsent is not usable here: a missing region is a legitimate null result, and
        // computeIfAbsent would retry the lookup on every frame for anything that does not resolve.
        if (regions.containsKey(id)) {
            return regions.get(id);
        }
        TextureRegion region = null;
        try {
            region = assets.region(id);
        } catch (RuntimeException ignored) {
            // treated as missing; the caller falls back
        }
        regions.put(id, region);
        return region;
    }

    public Drawable drawable(String id) {
        if (drawables.containsKey(id)) {
            return drawables.get(id);
        }
        TextureRegion region = region(id);
        Drawable drawable = region == null ? null : new TextureRegionDrawable(region);
        drawables.put(id, drawable);
        return drawable;
    }

    // A stretchable version, for panels that have to fit whatever is inside them. The border is given
    // as a fraction because these regions are stored at the atlas's own scale (a 38x38 source arrives
    // as 38x38 here but a 79x79 button as 79x79), so a fixed pixel border would eat a small image
    // entirely and leave a large one with a hairline edge.
    public Drawable stretchable(String id, float borderFraction) {
        String key = id + "#" + borderFraction;
        if (drawables.containsKey(key)) {
            return drawables.get(key);
        }
        TextureRegion region = region(id);
        Drawable drawable = null;
        if (region != null) {
            int bx = Math.max(1, (int) (region.getRegionWidth() * borderFraction));
            int by = Math.max(1, (int) (region.getRegionHeight() * borderFraction));
            // Clamped so the four borders can never overlap, which NinePatch rejects outright.
            bx = Math.min(bx, (region.getRegionWidth() - 1) / 2);
            by = Math.min(by, (region.getRegionHeight() - 1) / 2);
            drawable = new NinePatchDrawable(new NinePatch(region, bx, bx, by, by));
        }
        drawables.put(key, drawable);
        return drawable;
    }
}
