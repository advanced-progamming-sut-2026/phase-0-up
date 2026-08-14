package views.gdx.core;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.List;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ObjectMap;

import java.util.IdentityHashMap;
import java.util.Map;

// Makes the skin's text sharp instead of merely smooth.
//
// The UI is laid out in a 1280x720 design space and the window is 1920x1080, so every glyph is drawn
// at 1.5x. The skin generates its fonts at the DESIGN size -- the body face is 16px -- and a 16px
// bitmap glyph blown up to 24px is soft no matter how it is filtered. Linear filtering (which this
// used to be the whole of) only chooses how the blur is shaped.
//
// The fix is to generate the glyphs at the size they are actually drawn and then scale the font's
// metrics back down, so layout is unchanged to the pixel while the glyph atlas carries several times
// the detail. Every number a screen passes to Scene2D still means what it did.
//
// The declarations are re-read from the skin's own JSON rather than hardcoded, so a skin update or a
// -Dpvz.skin override is picked up instead of silently ignored.
final class SkinFonts {

    // Generate at this multiple of the declared size. 2 rather than the 1.5 the window happens to need,
    // so text stays crisp if the window is dragged larger -- and because a font atlas is generated once
    // at start-up, where a few extra megabytes cost nothing.
    private static final float SUPERSAMPLE = 2f;

    private static final String GENERATOR_SECTION =
            "com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator";

    private SkinFonts() {
    }

    // Regenerates every font the skin declares. Falls back to plain smoothing if the JSON cannot be
    // read: soft text is a far better outcome than a game that will not start.
    static void sharpen(Skin skin, FileHandle skinJson) {
        JsonValue declarations = readDeclarations(skinJson);
        if (declarations == null) {
            smooth(skin);
            return;
        }

        Map<BitmapFont, BitmapFont> replacements = new IdentityHashMap<>();
        for (JsonValue declaration = declarations.child; declaration != null;
                declaration = declaration.next) {
            BitmapFont existing = skin.optional(declaration.name(), BitmapFont.class);
            if (existing == null) {
                continue;
            }
            BitmapFont sharper = regenerate(skin, skinJson.parent(), declaration);
            if (sharper == null) {
                continue;
            }
            replacements.put(existing, sharper);
            skin.add(declaration.name(), sharper, BitmapFont.class);
        }

        if (replacements.isEmpty()) {
            smooth(skin);
            return;
        }

        repointStyles(skin, replacements);
        // Safe only because repointStyles covers every style class this skin declares that carries a
        // font -- Label, TextButton, TextField and List. A missed one would hold a disposed font and
        // draw garbage, so anything added to the skin later must be added there too.
        for (BitmapFont replaced : replacements.keySet()) {
            replaced.dispose();
        }
        Gdx.app.log("SkinFonts", "regenerated " + replacements.size() + " fonts at "
                + SUPERSAMPLE + "x for sharper text");
    }

    private static JsonValue readDeclarations(FileHandle skinJson) {
        try {
            if (skinJson == null || !skinJson.exists()) {
                return null;
            }
            return new JsonReader().parse(skinJson).get(GENERATOR_SECTION);
        } catch (RuntimeException e) {
            Gdx.app.error("SkinFonts", "could not read font declarations: " + e.getMessage());
            return null;
        }
    }

    private static BitmapFont regenerate(Skin skin, FileHandle skinDirectory, JsonValue declaration) {
        FileHandle ttf = skinDirectory.child(declaration.getString("font", ""));
        if (!ttf.exists()) {
            return null;
        }
        FreeTypeFontGenerator generator = null;
        try {
            generator = new FreeTypeFontGenerator(ttf);
            BitmapFont font = generator.generateFont(parameters(skin, declaration));
            // Scale the METRICS back down. The glyphs keep their extra pixels; every advance, line
            // height and bounds calculation reports what it did before, so no layout moves.
            font.getData().setScale(1f / SUPERSAMPLE);
            // At a fractional scale, snapping glyphs to whole pixels lands letters unevenly and spacing
            // visibly wobbles along a word.
            font.setUseIntegerPositions(false);
            return font;
        } catch (RuntimeException e) {
            Gdx.app.error("SkinFonts", "could not regenerate " + declaration.name() + ": "
                    + e.getMessage());
            return null;
        } finally {
            if (generator != null) {
                generator.dispose();
            }
        }
    }

    // Mirrors the skin's own declaration, with everything measured in pixels scaled up to match.
    private static FreeTypeFontGenerator.FreeTypeFontParameter parameters(Skin skin,
                                                                         JsonValue declaration) {
        FreeTypeFontGenerator.FreeTypeFontParameter parameter =
                new FreeTypeFontGenerator.FreeTypeFontParameter();
        parameter.size = Math.round(declaration.getFloat("size", 16f) * SUPERSAMPLE);
        parameter.mono = declaration.getBoolean("mono", false);
        parameter.gamma = declaration.getFloat("gamma", 1.8f);
        parameter.renderCount = declaration.getInt("renderCount", 2);
        parameter.kerning = declaration.getBoolean("kerning", true);
        parameter.flip = declaration.getBoolean("flip", false);
        parameter.color = color(skin, declaration.getString("color", null), Color.WHITE);

        // The outline has to grow with the glyph. Left at its declared width it would come out a third
        // as thick relative to the letter, which is exactly the detail the outlined title face exists
        // for.
        parameter.borderWidth = declaration.getFloat("borderWidth", 0f) * SUPERSAMPLE;
        parameter.borderColor = color(skin, declaration.getString("borderColor", null), Color.BLACK);
        parameter.borderStraight = declaration.getBoolean("borderStraight", false);
        parameter.borderGamma = declaration.getFloat("borderGamma", 1.8f);

        parameter.shadowOffsetX = Math.round(declaration.getFloat("shadowOffsetX", 0f) * SUPERSAMPLE);
        parameter.shadowOffsetY = Math.round(declaration.getFloat("shadowOffsetY", 0f) * SUPERSAMPLE);
        parameter.shadowColor = color(skin, declaration.getString("shadowColor", null),
                new Color(0f, 0f, 0f, 0.75f));
        parameter.spaceX = Math.round(declaration.getFloat("spaceX", 0f) * SUPERSAMPLE);
        parameter.spaceY = Math.round(declaration.getFloat("spaceY", 0f) * SUPERSAMPLE);

        String characters = declaration.getString("characters", null);
        if (characters != null && !characters.isEmpty()) {
            parameter.characters = characters;
        }

        // The skin asks for Nearest, which is right for a font drawn at exactly its generated size and
        // wrong for one that is scaled at all. These always are.
        parameter.minFilter = Texture.TextureFilter.Linear;
        parameter.magFilter = Texture.TextureFilter.Linear;
        parameter.hinting = hinting(declaration.getString("hinting", "AutoMedium"));
        return parameter;
    }

    private static FreeTypeFontGenerator.Hinting hinting(String name) {
        try {
            return FreeTypeFontGenerator.Hinting.valueOf(name);
        } catch (IllegalArgumentException e) {
            return FreeTypeFontGenerator.Hinting.AutoMedium;
        }
    }

    // Colours are declared by skin name ("white", "Black"), not as literals.
    private static Color color(Skin skin, String name, Color fallback) {
        if (name == null) {
            return fallback;
        }
        Color declared = skin.optional(name, Color.class);
        return declared == null ? fallback : new Color(declared);
    }

    // Styles hold a direct reference to the BitmapFont they were built with, so replacing the entry in
    // the skin's resource map is not enough on its own -- every style that used the old object has to
    // be pointed at the new one.
    private static void repointStyles(Skin skin, Map<BitmapFont, BitmapFont> replacements) {
        for (Label.LabelStyle style : values(skin, Label.LabelStyle.class)) {
            style.font = swap(style.font, replacements);
        }
        for (TextButton.TextButtonStyle style : values(skin, TextButton.TextButtonStyle.class)) {
            style.font = swap(style.font, replacements);
        }
        for (TextField.TextFieldStyle style : values(skin, TextField.TextFieldStyle.class)) {
            style.font = swap(style.font, replacements);
        }
        for (List.ListStyle style : values(skin, List.ListStyle.class)) {
            style.font = swap(style.font, replacements);
        }
    }

    private static <T> Iterable<T> values(Skin skin, Class<T> type) {
        ObjectMap<String, T> all = skin.getAll(type);
        return all == null ? java.util.List.of() : all.values();
    }

    private static BitmapFont swap(BitmapFont font, Map<BitmapFont, BitmapFont> replacements) {
        BitmapFont sharper = replacements.get(font);
        return sharper == null ? font : sharper;
    }

    // The old behaviour, kept as the fallback: linear filtering and no integer snapping. It cannot make
    // an upscaled glyph sharp, but it stops it being blocky as well as soft.
    private static void smooth(Skin skin) {
        for (BitmapFont font : skin.getAll(BitmapFont.class).values()) {
            font.setUseIntegerPositions(false);
            for (com.badlogic.gdx.graphics.g2d.TextureRegion page : font.getRegions()) {
                page.getTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            }
        }
    }
}
