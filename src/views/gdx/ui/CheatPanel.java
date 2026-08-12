package views.gdx.ui;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import views.gdx.core.Assets;

// The cheat commands, as buttons.
//
// Every one of these is typeable at the terminal prompt and several are needed constantly while
// testing -- topping up sun to try an expensive plant, clearing recharges, spawning a zombie to watch
// one animation. Retyping "cheat add -n 500 suns" for the hundredth time is the kind of friction that
// stops people from testing at all.
//
// The buttons synthesise the SAME strings, through the same CommandBridge sink as every click on the
// lawn. Nothing here can do anything the prompt could not, which is what keeps it honest: this is a
// faster way to type, not a second set of rules.
public final class CheatPanel extends Table {

    // Label and the command it sends. Wave-advance is deliberately a big jump: single ticks are what
    // the keyboard is for, and the reason to reach for this is to skip ahead.
    private static final String[][] CHEATS = {
        {"+500 Sun", "cheat add -n 500 suns"},
        {"+Plant Food", "cheat add-plant-food"},
        {"Clear Cooldowns", "cheat remove-cooldown"},
        {"Skip 10s", "advance time -t 100 ticks"},
        {"Spawn Zombie", "cheat spawn-zombie -t normal -l (8, 2)"},
        {"Nuke Everything", "release the nuke"},
    };

    private final java.util.function.Predicate<String> sink;

    public CheatPanel(Assets assets, UiArt art, java.util.function.Predicate<String> sink) {
        this.sink = sink;

        setBackground(art.stretchable(UiArt.PANEL, 0.28f));
        pad(8f);

        Label title = new Label("Cheats  [C]", assets.skin());
        title.setAlignment(Align.center);
        add(title).padBottom(6f).row();

        for (String[] cheat : CHEATS) {
            add(button(assets, art, cheat[0], cheat[1])).width(150f).padBottom(4f).row();
        }
    }

    private Table button(Assets assets, UiArt art, String text, String command) {
        Table button = new Table();
        button.setBackground(art.stretchable(UiArt.SEED_PACKET, 0.22f));
        button.pad(5f);
        Label label = new Label(text, assets.skin());
        label.setAlignment(Align.center);
        button.add(label).center();
        button.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                sink.test(command);
            }
        });
        return button;
    }
}
