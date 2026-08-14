package views.gdx.core;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.Disposable;
import pvz.libpvz.pam.PamPlayer;
import pvz.libpvz.textures.TextureBank;

import java.io.File;

// The one place that loads anything, and the one place that disposes anything.
//
// Screens and renderers ask this for handles and never load or dispose a Texture themselves. That rule
// is what keeps the GUI leak-free without anyone having to reason about ownership: if it was not
// created here, it does not need disposing here, and nothing else is allowed to create it.
public final class Assets implements Disposable {

    // Where the extracted PvZ2 data lives. Overridable so the folder can sit outside the repo:
    //   gradlew runGui -Dpvz.assets=D:/pvz-assets
    private static final String ASSET_ROOT_PROPERTY = "pvz.assets";
    private static final String DEFAULT_ASSET_ROOT = "pvz-assets";

    // pvz-assets ships several resolutions; 768 is the one whose atlases are complete here and the
    // resolution every path in animations.json is written against.
    private static final String RESOLUTION = "768";

    // What a usable asset root has to contain. Checked up front so a missing folder fails with one
    // clear sentence instead of a NullPointerException five frames into the first level.
    private static final String[] REQUIRED_ENTRIES = {"ATLASES", "IMAGES", "RESOURCES.json"};

    private final FileHandle root;
    private final TextureBank bank;
    private final PamPlayer pam;
    private final Skin skin;

    // A 1x1 opaque white texture, tinted per use. The skin ships no plain white drawable and no Window
    // style, so panels, toast backgrounds and tile highlights need a neutral fill to tint. Owned here
    // rather than by whoever needs it first, so the "only Assets disposes" rule keeps holding.
    private final com.badlogic.gdx.graphics.Texture whitePixel;

    // A soft-edged white disc. Tinting a 1x1 pixel gives a SQUARE, which is why coloured projectiles
    // rendered as rectangles over the pea sprite. Anything round and tintable -- element shots, impact
    // shards -- uses this instead.
    private final com.badlogic.gdx.graphics.Texture disc;

    public Assets() {
        this.root = resolveAssetRoot();
        verifyAssetRoot(this.root);

        this.bank = new TextureBank(RESOLUTION, root);
        this.pam = new PamPlayer(bank, root);

        // Ships inside the pvz-skin jar as classpath resources (skin/pvz2_skin.json + atlas + TTFs).
        // Gdx.files.internal() falls back to the classpath, so this needs nothing on disk.
        this.skin = loadSkin();
        // Re-renders the skin's fonts at the resolution they are actually drawn at. See SkinFonts:
        // the declared sizes suit the 1280x720 design space, and the window is 1920x1080.
        SkinFonts.sharpen(this.skin, skinFile());
        this.whitePixel = createWhitePixel();
        this.disc = createDisc();

        Gdx.app.log("Assets", "asset root: " + root.file().getAbsolutePath());
    }

    // Where the skin's own JSON is, so its font declarations can be re-read. The TTFs sit beside it,
    // which is why SkinFonts is handed the file rather than the parsed Skin.
    private static FileHandle skinFile() {
        String path = System.getProperty("pvz.skin");
        if (path != null && !path.isBlank()) {
            FileHandle file = Gdx.files.absolute(new File(path.trim()).getAbsolutePath());
            if (file.exists()) {
                return file;
            }
        }
        // Classpath, out of the pvz-skin jar -- the same handle PvzSkin itself loads.
        return Gdx.files.internal("skin/pvz2_skin.json");
    }

    // The skin normally comes out of the pvz-skin jar, which makes it convenient but uneditable. Point
    // -Dpvz.skin at an extracted pvz2_skin.json to work on it:
    //
    //   1. unzip the jar's skin/ folder somewhere (see documents/phase-2-onboarding.md)
    //   2. gradlew runGui -Dpvz.skin=C:/work/skin/pvz2_skin.json
    //   3. edit the JSON (or re-export it from Skin Composer) and relaunch
    //
    // FreeTypeSkin rather than plain Skin: this skin declares TTF fonts that a stock Skin loader does
    // not know how to build.
    private static Skin loadSkin() {
        String path = System.getProperty("pvz.skin");
        if (path == null || path.isBlank()) {
            return pvz.skin.PvzSkin.get();
        }
        FileHandle file = Gdx.files.absolute(new File(path.trim()).getAbsolutePath());
        if (!file.exists()) {
            Gdx.app.error("Assets", "-Dpvz.skin=" + path + " does not exist; using the bundled skin");
            return pvz.skin.PvzSkin.get();
        }
        Gdx.app.log("Assets", "loading skin from disk: " + file.path());
        return new pvz.skin.FreeTypeSkin(file);
    }

    // Drawn once at 64px and scaled down in use, so the edge stays smooth at any projectile size.
    // Antialiased by hand, per pixel.
    //
    // Pixmap.fillCircle does no antialiasing whatsoever -- it is a hard, stair-stepped stamp -- and
    // linear filtering only smears those steps rather than removing them, which is why the level nodes
    // came out visibly jagged. Computing coverage from the distance to the centre gives a genuinely
    // smooth edge, and 256px means it is still smooth when a node is drawn large.
    private static com.badlogic.gdx.graphics.Texture createDisc() {
        int size = 256;
        com.badlogic.gdx.graphics.Pixmap pixmap = new com.badlogic.gdx.graphics.Pixmap(
                size, size, com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);
        try {
            // Straight writes, or each pixel would be blended against the one already there.
            pixmap.setBlending(com.badlogic.gdx.graphics.Pixmap.Blending.None);
            float centre = size / 2f;
            float radius = centre - 1f;
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    float dx = x + 0.5f - centre;
                    float dy = y + 0.5f - centre;
                    float distance = (float) Math.sqrt(dx * dx + dy * dy);
                    // One pixel of ramp at the rim: fully opaque inside, fading to nothing across the
                    // last pixel. Any wider reads as a glow rather than an edge.
                    float alpha = Math.max(0f, Math.min(1f, radius - distance));
                    pixmap.setColor(1f, 1f, 1f, alpha);
                    pixmap.drawPixel(x, y);
                }
            }
            com.badlogic.gdx.graphics.Texture texture = new com.badlogic.gdx.graphics.Texture(pixmap);
            // Linear filtering: without it the circle's edge is visibly stair-stepped once scaled.
            texture.setFilter(com.badlogic.gdx.graphics.Texture.TextureFilter.Linear,
                    com.badlogic.gdx.graphics.Texture.TextureFilter.Linear);
            return texture;
        } finally {
            pixmap.dispose();
        }
    }

    // A tintable round fill, for projectiles and impact bursts.
    public com.badlogic.gdx.scenes.scene2d.utils.Drawable round(com.badlogic.gdx.graphics.Color color) {
        return new com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable(
                new com.badlogic.gdx.graphics.g2d.TextureRegion(disc)).tint(color);
    }

    private static com.badlogic.gdx.graphics.Texture createWhitePixel() {
        com.badlogic.gdx.graphics.Pixmap pixmap = new com.badlogic.gdx.graphics.Pixmap(
                1, 1, com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);
        try {
            pixmap.setColor(com.badlogic.gdx.graphics.Color.WHITE);
            pixmap.fill();
            return new com.badlogic.gdx.graphics.Texture(pixmap);
        } finally {
            // The Texture has copied the pixels to the GPU; the CPU-side Pixmap is dead weight now and
            // is one of the easiest things in LibGDX to leak.
            pixmap.dispose();
        }
    }

    // A tintable solid fill. Returns a NEW drawable each call so callers can set colours independently
    // without stepping on each other -- the underlying texture is shared and disposed once.
    public com.badlogic.gdx.scenes.scene2d.utils.Drawable solid(com.badlogic.gdx.graphics.Color color) {
        com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable drawable =
                new com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable(
                        new com.badlogic.gdx.graphics.g2d.TextureRegion(whitePixel));
        return drawable.tint(color);
    }

    // Atlas pages stream in on background threads; this hands the finished ones to the GL thread.
    // Must be called once per frame or asynchronously-requested textures never actually appear.
    public void update() {
        bank.update();
    }

    public TextureBank bank() {
        return bank;
    }

    public PamPlayer pam() {
        return pam;
    }

    public Skin skin() {
        return skin;
    }

    public FileHandle root() {
        return root;
    }

    // A single sub-image by its RESOURCES.json id, e.g. "IMAGE_PLANT_PEASHOOTER_PEASHOOTER_101X76".
    // Returns null when the id is unknown -- callers fall back to a placeholder rather than crashing.
    public TextureRegion region(String imageId) {
        return bank.region(imageId);
    }

    private static FileHandle resolveAssetRoot() {
        String configured = System.getProperty(ASSET_ROOT_PROPERTY);
        String path = (configured == null || configured.isBlank()) ? DEFAULT_ASSET_ROOT : configured.trim();

        File file = new File(path);
        // Gdx.files.local resolves against the working directory, which runGui pins to the project root;
        // an explicitly configured absolute path has to bypass that.
        return file.isAbsolute() ? Gdx.files.absolute(file.getAbsolutePath()) : Gdx.files.local(path);
    }

    // The most likely first-run failure for anyone cloning this repo, because pvz-assets/ is git-ignored
    // (about 510 MB) and therefore absent on a fresh checkout. Worth a real message: this is the ONLY
    // thing that stops a fresh clone from running the GUI -- the build and the terminal game are fine.
    private static void verifyAssetRoot(FileHandle root) {
        if (!root.exists() || !root.isDirectory()) {
            throw new IllegalStateException(assetRootMessage(root, "was not found"));
        }
        for (String entry : REQUIRED_ENTRIES) {
            if (!root.child(entry).exists()) {
                throw new IllegalStateException(assetRootMessage(root, "is missing " + entry));
            }
        }
    }

    private static String assetRootMessage(FileHandle root, String problem) {
        String nl = System.lineSeparator();
        return "The PvZ asset folder " + problem + ": " + root.file().getAbsolutePath() + nl
                + nl
                + "  This is expected on a fresh clone. pvz-assets/ is about 510 MB of extracted" + nl
                + "  PopCap art, far too big to keep in git, so it is shared separately -- ask a" + nl
                + "  teammate for the folder. Nothing else is missing: `gradlew run` and" + nl
                + "  `gradlew build` both work without it." + nl
                + nl
                + "  Then either put it in the project root as pvz-assets/ (containing ATLASES/," + nl
                + "  IMAGES/ and RESOURCES.json), or leave it where it is and add this line to your" + nl
                + "  personal ~/.gradle/gradle.properties:" + nl
                + nl
                + "      systemProp." + ASSET_ROOT_PROPERTY + "=D:/path/to/pvz-assets" + nl
                + nl
                + "  For one run only: gradlew runGui -D" + ASSET_ROOT_PROPERTY + "=D:/path/to/pvz-assets" + nl
                + "  See README.md.";
    }

    // Artwork that is NOT part of the PopCap dump: our own images, loaded from a plain file rather than
    // resolved through RESOURCES.json and the atlas bank.
    //
    // Cached by path and disposed with everything else here, so the "only Assets loads, only Assets
    // disposes" rule still holds. A missing file returns null and the caller falls back -- these are
    // decoration, and the game must start without them.
    private final java.util.Map<String, com.badlogic.gdx.graphics.Texture> ownArt =
            new java.util.HashMap<>();

    public com.badlogic.gdx.graphics.g2d.TextureRegion ownArt(String path) {
        if (ownArt.containsKey(path)) {
            com.badlogic.gdx.graphics.Texture cached = ownArt.get(path);
            return cached == null ? null : new TextureRegion(cached);
        }
        com.badlogic.gdx.graphics.Texture texture = null;
        try {
            FileHandle file = Gdx.files.internal(path);
            if (file.exists()) {
                texture = new com.badlogic.gdx.graphics.Texture(file);
                // These are photographic-scale backgrounds drawn at arbitrary sizes; without linear
                // filtering the downscale to a 1280-wide viewport aliases badly.
                texture.setFilter(com.badlogic.gdx.graphics.Texture.TextureFilter.Linear,
                        com.badlogic.gdx.graphics.Texture.TextureFilter.Linear);
            } else {
                Gdx.app.log("Assets", "no art at " + path + " -- falling back");
            }
        } catch (RuntimeException e) {
            Gdx.app.error("Assets", "could not load " + path + ": " + e.getMessage());
        }
        ownArt.put(path, texture);
        return texture == null ? null : new TextureRegion(texture);
    }

    @Override
    public void dispose() {
        // Disposed in reverse order of creation. PamPlayer holds no GL resources of its own -- it draws
        // through the bank -- so there is deliberately nothing to dispose for it.
        for (com.badlogic.gdx.graphics.Texture texture : ownArt.values()) {
            if (texture != null) {
                texture.dispose();
            }
        }
        ownArt.clear();
        whitePixel.dispose();
        disc.dispose();
        skin.dispose();
        bank.dispose();
    }
}
