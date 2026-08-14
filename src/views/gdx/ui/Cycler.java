package views.gdx.ui;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;

// "< option >" -- pick one of a short, fixed list.
//
// A dropdown would be the obvious widget, but the skin declares no SelectBoxStyle and inventing one
// would mean hand-drawing a list background next to authentic art. The atlas does ship a matched pair
// of green arrows, and every choice these menus offer is short enough to step through: five security
// questions, two genders, five difficulty levels, three game speeds.
public final class Cycler extends Table {

    private final String[] options;
    private final Label current;

    // What actually scales for the pop.
    //
    // A Label ignores its own actor scale entirely -- it draws through a BitmapFontCache and never
    // consults getScaleX -- so scaleTo on the label is a no-op that looks like a working animation in
    // the source. A transform-enabled group around it does apply the scale to whatever it contains.
    private final com.badlogic.gdx.scenes.scene2d.ui.Container<Label> valueBox;

    private int index;

    // Fired on every change, so a caller can apply the choice immediately rather than on submit.
    private java.util.function.IntConsumer onChange;

    public Cycler(Skin skin, String[] options, int startIndex) {
        this.options = options.clone();
        this.index = clamp(startIndex);
        this.current = MenuStyles.label(skin, options[this.index], MenuStyles.TEXT);
        this.current.setAlignment(Align.center);

        this.valueBox = new com.badlogic.gdx.scenes.scene2d.ui.Container<>(current);
        this.valueBox.setTransform(true);

        // Tight, so the three read as one control. Spread wide with the value floating in the middle,
        // the arrows looked like two unrelated buttons that happened to share a row.
        add(arrow(skin, MenuStyles.ARROW_LEFT, -1)).size(30f).padRight(4f);
        add(valueBox).minWidth(140f).pad(0f, 4f, 0f, 4f);
        add(arrow(skin, MenuStyles.ARROW_RIGHT, 1)).size(30f).padLeft(4f);
    }

    public Cycler onChange(java.util.function.IntConsumer listener) {
        this.onChange = listener;
        return this;
    }

    public int selectedIndex() {
        return index;
    }

    public String selected() {
        return options[index];
    }

    // A missing arrow region degrades to a clickable "<" / ">" rather than an invisible hit area.
    private Table arrow(Skin skin, String regionName, int step) {
        Drawable art = MenuStyles.drawable(skin, regionName);
        Table button = new Table();
        if (art != null) {
            button.add(new Image(art)).grow();
        } else {
            button.add(MenuStyles.label(skin, step < 0 ? "<" : ">", MenuStyles.TEXT)).grow();
        }
        // The arrows get the same swell-and-press every other control has.
        ButtonJuice.applyTo(button);
        button.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                step(step);
            }
        });
        return button;
    }

    // Wraps, so the last option is one click from the first. With five items that is the difference
    // between one click and four.
    private void step(int delta) {
        index = Math.floorMod(index + delta, options.length);
        current.setText(options[index]);
        // A quick pop, so a change registers even when two adjacent options read almost the same
        // ("2 - Easy" to "3 - Normal"). Restarted each time: held arrow clicks should not queue.
        valueBox.setOrigin(com.badlogic.gdx.utils.Align.center);
        valueBox.clearActions();
        valueBox.setScale(1f);
        valueBox.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.sequence(
                com.badlogic.gdx.scenes.scene2d.actions.Actions.scaleTo(1.1f, 1.1f, 0.07f,
                        com.badlogic.gdx.math.Interpolation.swingOut),
                com.badlogic.gdx.scenes.scene2d.actions.Actions.scaleTo(1f, 1f, 0.12f,
                        com.badlogic.gdx.math.Interpolation.sine)));
        if (onChange != null) {
            onChange.accept(index);
        }
    }

    private int clamp(int value) {
        return value < 0 || value >= options.length ? 0 : value;
    }
}
