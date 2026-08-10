import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import views.gdx.core.PvZGame;

// Entry point of the graphical build: `gradlew runGui`.
//
// The terminal build still starts at Main and is not going away -- it drives the same models, systems
// and Commands, so it stays useful as the regression harness for anything changed on the GUI side.
// The only difference between the two front ends is which renderer implementations get injected.
public class DesktopLauncher {

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Plants vs. Zombies 2 -- Sharif AP");
        config.setWindowedMode((int) PvZGame.VIRTUAL_WIDTH, (int) PvZGame.VIRTUAL_HEIGHT);

        // The lawn is a 5x9 grid that must stay fully visible, so the window has a floor below which
        // the board would stop being readable. FitViewport letterboxes anything above it.
        config.setWindowSizeLimits(960, 540, -1, -1);

        // vsync on, with a foreground cap as a backstop for machines that ignore it. The simulation is
        // driven by a fixed-step accumulator rather than by frame rate, so this only affects smoothness
        // -- the game plays at the same speed either way.
        config.useVsync(true);
        config.setForegroundFPS(60);

        new Lwjgl3Application(new PvZGame(), config);
    }
}
