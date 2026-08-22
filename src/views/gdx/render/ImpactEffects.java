package views.gdx.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import views.gdx.sprite.ClipMap;
import views.gdx.sprite.EntitySprite;
import views.gdx.sprite.SpriteRegistry;

import java.util.ArrayList;
import java.util.List;

// Short bursts where a shot lands.
//
// The model has no notion of an impact -- Projectile.onHit applies damage and sets isDestroyed, and
// the object is swept the same tick -- so there is no event to listen to. The view infers it instead:
// GameRenderer notices a projectile that was in flight last frame and is gone this frame, and asks
// for a burst at its last drawn position. That covers zombie hits, grave hits and shots that expire
// on the board edge alike, without the model growing an "impact happened" flag it does not need.
//
// The burst is the game's own splat ANIMATION (T_SPLAT_PEA_ROCK), played once. Earlier versions drew a
// ring of generated discs, then a ring of scattered copies of one static splat frame; both were
// stand-ins for an animation that was in the dump all along.
public final class ImpactEffects {

    // One splat animation per element. These live under 768/INITIAL/EFFECTS/, not 768/FULL/ -- a search
    // of FULL alone finds only T_SPLAT_PEA_ROCK and concludes, wrongly, that ice and fire have no splat
    // of their own. Both trees have to be searched.
    private static final java.util.Map<models.entities.projectiles.Element, String> SPLAT_BY_ELEMENT =
            new java.util.EnumMap<>(models.entities.projectiles.Element.class);

    private static final String SPLAT_DEFAULT = "T_SPLAT_PEA";

    static {
        SPLAT_BY_ELEMENT.put(models.entities.projectiles.Element.NEUTRAL, "T_SPLAT_PEA");
        SPLAT_BY_ELEMENT.put(models.entities.projectiles.Element.ICE, "T_SPLAT_SNOW_PEA");
        SPLAT_BY_ELEMENT.put(models.entities.projectiles.Element.FIRE, "T_SPLAT_FIRE_PEA");
    }

    // Shots whose splat belongs to the PLANT rather than to an element.
    //
    // Checked before the element map, because element is the wrong axis for these: a rutabaga and a
    // corn kernel are both NEUTRAL, and both were bursting into a pea splat. Kernel-pult even ships two
    // -- the kernel scatters, the butter splatters -- and the model already distinguishes them, so the
    // view can too.
    private static final java.util.Map<models.entities.projectiles.ProjectileType, String>
            SPLAT_BY_TYPE = new java.util.EnumMap<>(models.entities.projectiles.ProjectileType.class);

    static {
        SPLAT_BY_TYPE.put(models.entities.projectiles.ProjectileType.RUTABAGA,
                "ROTORUTABAGA_PROJECTILE_HIT");
        SPLAT_BY_TYPE.put(models.entities.projectiles.ProjectileType.CORN_KERNEL,
                "SPLAT_KERNALPULT_KERNAL");
        SPLAT_BY_TYPE.put(models.entities.projectiles.ProjectileType.BUTTER,
                "SPLAT_KERNALPULT_BUTTER");
        // Each lobbed fruit bursts as itself. A cabbage that leaves a pea splat is the same lie as a
        // Cabbage-pult throwing a pea, one frame later.
        SPLAT_BY_TYPE.put(models.entities.projectiles.ProjectileType.CABBAGE,
                "SPLAT_CABBAGEPULT");
        SPLAT_BY_TYPE.put(models.entities.projectiles.ProjectileType.MELON,
                "T_SPLAT_MELONPULT");
        SPLAT_BY_TYPE.put(models.entities.projectiles.ProjectileType.WINTER_MELON,
                "T_SPLAT_WINTERMELON");
        SPLAT_BY_TYPE.put(models.entities.projectiles.ProjectileType.PEPPER,
                "T_PEPPERPULT_PROJECTILE_SPLAT");
        SPLAT_BY_TYPE.put(models.entities.projectiles.ProjectileType.THORN,
                "CACTUS_PROJECTILE_HIT");
        SPLAT_BY_TYPE.put(models.entities.projectiles.ProjectileType.STAR,
                "T_STARFRUIT_PROJECTILE_HIT");
        SPLAT_BY_TYPE.put(models.entities.projectiles.ProjectileType.PIERCING_SPIKE,
                "T_REDSTINGER_PROJECTILE_HIT");
    }

    // How wide each splat is drawn. A melon bursting is a much bigger event than a thorn landing, and
    // one size for all of them made the fruit splats look like specks.
    private static final java.util.Map<String, Float> SPLAT_WIDTH_BY_SPRITE = java.util.Map.of(
            "SPLAT_CABBAGEPULT", 0.75f,
            "T_SPLAT_MELONPULT", 0.95f,
            "T_SPLAT_WINTERMELON", 0.95f,
            "T_PEPPERPULT_PROJECTILE_SPLAT", 0.80f);

    // Clip variants, so repeated hits do not look stamped from one mould. T_SPLAT_PEA ships six;
    // firstAvailable falls back for the splats that ship fewer.
    private static final String[] CLIPS = {"animation", "animation2", "animation3"};

    // The splat art is authored far larger than a pea impact needs (its box is ~240 PAM units wide),
    // so it is scaled down to roughly a third of a tile.
    private static final float SPLAT_WIDTH_CELLS = 0.34f;

    private static final float LIFETIME = 0.30f;

    // A muzzle flash is drawn about half a tile across -- larger than a splat, because it is the plant's
    // own effect rather than a mark left on a zombie.
    private static final float MUZZLE_WIDTH_CELLS = 0.55f;

    // A strike travels from the plant to whatever it hit, so it needs longer than a splat and its own
    // size: the cloud and the dirt are plant-sized effects, not marks left on a zombie.
    private static final float STRIKE_WIDTH_CELLS = 0.9f;
    // Matched to GlobalTargetingAbility.WIND_UP_TICKS (5 ticks at 10 Hz). The effect has to arrive on
    // the frame the damage lands, so the two are the same half-second by construction rather than by
    // coincidence -- change one and the other has to move with it.
    private static final float STRIKE_LIFETIME = 0.5f;

    private static final class Burst {
        float x;
        float y;
        // Where it ends up. Equal to x/y for the ones that stay put, which is most of them.
        float toX;
        float toY;
        float age;
        float lifetime;
        float widthCells;
        String clip;
        String sprite;
        Color color;
    }

    private final SpriteRegistry sprites;
    private final List<Burst> bursts = new ArrayList<>();
    private int nextClip;

    public ImpactEffects(SpriteRegistry sprites) {
        this.sprites = sprites;
    }

    // World position, in the same space the projectile was drawn in. type may be null.
    public void spawn(float worldX, float worldY, Color color,
                      models.entities.projectiles.Element element,
                      models.entities.projectiles.ProjectileType type) {
        Burst burst = new Burst();
        burst.x = worldX;
        burst.y = worldY;
        // A splat STAYS WHERE IT LANDED. Leaving these at their 0f default made drawSplat interpolate
        // every splat from the hit toward world (0, 0) over its life -- a stream of pea fragments
        // sliding off to the bottom-left corner of the board after every single shot. It also tripped
        // the travelling-strike fade below, which holds full opacity for two thirds of the flight, so
        // the wrong thing was drawn brightly rather than subtly.
        burst.toX = worldX;
        burst.toY = worldY;
        burst.color = new Color(color);
        String byType = type == null ? null : SPLAT_BY_TYPE.get(type);
        burst.sprite = byType != null ? byType : SPLAT_BY_ELEMENT.getOrDefault(
                element == null ? models.entities.projectiles.Element.NEUTRAL : element,
                SPLAT_DEFAULT);
        burst.widthCells = SPLAT_WIDTH_BY_SPRITE.getOrDefault(burst.sprite, SPLAT_WIDTH_CELLS);
        // Cycled rather than random, so identical runs stay pixel-reproducible for the screenshot
        // harness -- the same reason the old burst used fixed angles.
        burst.clip = CLIPS[nextClip++ % CLIPS.length];
        bursts.add(burst);
    }

    // A named one-shot effect at a point -- the flash at a plant's mouth as it fires, rather than the
    // mark a shot leaves where it lands. Same machinery, different art and a different size.
    public void spawnMuzzle(float worldX, float worldY, String spriteName) {
        add(worldX, worldY, worldX, worldY, spriteName, MUZZLE_WIDTH_CELLS, LIFETIME);
    }

    // A strike: an effect that TRAVELS, from the plant that struck to whatever it struck.
    //
    // Caulipower and Electric Blueberry hit a zombie anywhere on the board with nothing in flight, so
    // there is no projectile to follow and no splat position to burst at -- only two points and the
    // knowledge that something went between them. Grave Buster uses the same call with both points the
    // same, because its dirt flies nowhere.
    public void spawnStrike(float fromX, float fromY, float toX, float toY, String spriteName) {
        add(fromX, fromY, toX, toY, spriteName, STRIKE_WIDTH_CELLS, STRIKE_LIFETIME);
    }

    private void add(float fromX, float fromY, float toX, float toY, String spriteName,
                     float widthCells, float lifetime) {
        if (spriteName == null) {
            return;
        }
        Burst burst = new Burst();
        burst.x = fromX;
        burst.y = fromY;
        burst.toX = toX;
        burst.toY = toY;
        burst.color = new Color(Color.WHITE);
        burst.sprite = spriteName;
        burst.widthCells = widthCells;
        burst.lifetime = lifetime;
        burst.clip = CLIPS[nextClip++ % CLIPS.length];
        bursts.add(burst);
    }

    public void draw(Batch batch, float delta, float cellSize) {
        if (bursts.isEmpty()) {
            return;
        }
        Color previous = batch.getColor().cpy();

        for (int i = bursts.size() - 1; i >= 0; i--) {
            Burst burst = bursts.get(i);
            burst.age += delta;
            float life = burst.lifetime > 0f ? burst.lifetime : LIFETIME;
            if (burst.age >= life) {
                bursts.remove(i);
                continue;
            }
            float t = burst.age / life;
            // Fades out over its life. The clip itself is short, so without this the last frame would
            // pop rather than dissolve. A travelling strike holds its colour until the last third, or
            // it is already half gone by the time it reaches what it hit.
            float fade = burst.toX == burst.x && burst.toY == burst.y
                    ? 1f - t : 1f - Math.max(0f, (t - 0.66f) / 0.34f);
            batch.setColor(burst.color.r, burst.color.g, burst.color.b, fade);
            drawSplat(batch, burst, SpritePlacer.toSpriteSpace(burst.widthCells * cellSize), t);
        }
        batch.setColor(previous);
    }

    private void drawSplat(Batch batch, Burst burst, float width, float t) {
        EntitySprite splat = sprites.get(burst.sprite);
        if (splat == null || !splat.isReady()) {
            return;
        }
        String clip = ClipMap.firstAvailable(splat, burst.clip);
        com.badlogic.gdx.math.Rectangle bounds = splat.bounds(clip);
        if (bounds == null || bounds.width <= 0f) {
            return;
        }
        float scale = width / bounds.width;
        // Straight-line travel. A strike sets toX/toY to what it hit; everything else leaves them equal
        // to its own position, so this is the identity for a splat or a muzzle flash.
        float drawX = burst.x + (burst.toX - burst.x) * t;
        float drawY = burst.y + (burst.toY - burst.y) * t;

        com.badlogic.gdx.math.Matrix4 previous = batch.getTransformMatrix().cpy();
        com.badlogic.gdx.math.Matrix4 scaled = new com.badlogic.gdx.math.Matrix4(previous)
                .translate(SpritePlacer.toSpriteSpace(drawX),
                        SpritePlacer.toSpriteSpace(drawY), 0f)
                .scale(scale, scale, 1f);

        batch.setTransformMatrix(scaled);
        // Same y-down correction as everywhere else: libPVZ reports bounds in the .PAM's Flash-style
        // coordinates, where the art hangs below the origin.
        splat.draw(batch, clip, ClipMap.sample(splat, clip, t * LIFETIME),
                0f, bounds.y + bounds.height / 2f, true);
        batch.setTransformMatrix(previous);
    }
}
