package views.gdx.core;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.ScreenUtils;

// Lets the graphical build be checked without a human sitting in front of it.
//
// A LibGDX window normally runs until someone closes it, which makes "did that change actually
// render?" impossible to answer from a script or a CI job. Two optional system properties fix that:
//
//   -Dpvz.smokeFrames=90            render 90 frames, then quit on its own
//   -Dpvz.screenshot=out/shot.png   write the final frame to a PNG before quitting
//
// Neither is set during normal play, so this costs one integer increment per frame and nothing else.
// Use it as the acceptance check for every rendering task: run the build, then look at the PNG.
public final class SmokeHarness {

    private static final String FRAMES_PROPERTY = "pvz.smokeFrames";
    private static final String SCREENSHOT_PROPERTY = "pvz.screenshot";

    // Screenshots taken on frame 1 catch a half-initialised window: fonts may not have uploaded and
    // the first buffer swap has not necessarily happened. Waiting a couple of frames costs nothing and
    // removes a whole class of confusing blank captures.
    private static final int MIN_WARMUP_FRAMES = 3;

    private final int exitAfterFrames;      // 0 -> run until the user closes the window
    private final String screenshotPath;    // null -> never capture

    private int framesRendered;
    private boolean captured;

    private SmokeHarness(int exitAfterFrames, String screenshotPath) {
        this.exitAfterFrames = exitAfterFrames;
        this.screenshotPath = screenshotPath;
    }

    public static SmokeHarness fromSystemProperties() {
        return new SmokeHarness(readFrameCount(), emptyToNull(System.getProperty(SCREENSHOT_PROPERTY)));
    }

    // A frame's worth of time when running deterministically. 60 Hz because that is what the window is
    // capped at, so a smoke run covers the same wall-clock span it would in normal play.
    private static final float FIXED_DELTA = 1f / 60f;

    // In smoke mode every frame advances by exactly FIXED_DELTA instead of by however long the frame
    // actually took.
    //
    // Without this, screenshots are not reproducible: animation phase comes from an accumulated real
    // delta, so two runs of the same build land on slightly different poses and a pixel diff shows
    // thousands of changed pixels for no reason. That makes visual regression testing useless -- it
    // cannot tell a real rendering change from frame-timing jitter. With it, identical builds produce
    // byte-identical PNGs, and any diff at all is a genuine change.
    public float step(float realDelta) {
        return isActive() ? FIXED_DELTA : realDelta;
    }

    // Whether anything at all was requested -- used only to decide if the launcher should announce it.
    public boolean isActive() {
        return exitAfterFrames > 0 || screenshotPath != null;
    }

    public String describe() {
        StringBuilder sb = new StringBuilder("smoke mode:");
        if (exitAfterFrames > 0) {
            sb.append(" exiting after ").append(exitAfterFrames).append(" frames;");
        }
        if (screenshotPath != null) {
            sb.append(" screenshot -> ").append(screenshotPath);
        }
        return sb.toString();
    }

    // Call once at the very end of render(), after everything has been drawn -- the back buffer still
    // holds this frame's pixels at that point, which is exactly what a screenshot needs.
    public void afterFrame() {
        if (!isActive()) {
            return;
        }
        framesRendered++;

        if (screenshotPath != null && !captured && framesRendered >= captureFrame()) {
            capture();
            captured = true;
        }
        if (exitAfterFrames > 0 && framesRendered >= exitAfterFrames) {
            Gdx.app.exit();
        }
    }

    // Capture on the last frame we are going to draw, so the PNG shows the final state rather than a
    // mid-startup one. With no frame budget set, settle for a short warmup.
    private int captureFrame() {
        return exitAfterFrames > 0 ? Math.max(exitAfterFrames, MIN_WARMUP_FRAMES) : MIN_WARMUP_FRAMES;
    }

    private void capture() {
        int width = Gdx.graphics.getBackBufferWidth();
        int height = Gdx.graphics.getBackBufferHeight();

        // flipY = true: OpenGL hands back rows bottom-up, and a PNG written straight from that reads
        // upside down. Flipping here rather than in the viewer keeps the file correct for everyone.
        byte[] pixels = ScreenUtils.getFrameBufferPixels(0, 0, width, height, true);

        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        try {
            BufferUtils.copy(pixels, 0, pixmap.getPixels(), pixels.length);
            com.badlogic.gdx.files.FileHandle file = Gdx.files.absolute(
                    new java.io.File(screenshotPath).getAbsolutePath());
            if (file.parent() != null) {
                file.parent().mkdirs();
            }
            PixmapIO.writePNG(file, pixmap);
            Gdx.app.log("SmokeHarness", "wrote " + width + "x" + height + " screenshot to " + file.path());
        } finally {
            pixmap.dispose();
        }
    }

    private static int readFrameCount() {
        String raw = emptyToNull(System.getProperty(FRAMES_PROPERTY));
        if (raw == null) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            // A typo here should not take the game down -- say so and behave as if it was not set.
            System.err.println("Ignoring -D" + FRAMES_PROPERTY + "=" + raw + " (not a number).");
            return 0;
        }
    }

    private static String emptyToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
