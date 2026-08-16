package views.gdx.ui;

import views.gdx.sprite.EntitySprite;

// A plant drawn small, inside a UI widget.
//
// All of the work is in EntityIcon, which does the same job for zombies. This name is kept because the
// seed bank and the seed-selection screen read better asking for a PlantIcon than for an EntityIcon of
// a plant, and because it is what those call sites already say.
public final class PlantIcon extends EntityIcon {

    public PlantIcon(EntitySprite sprite) {
        super(sprite);
    }
}
