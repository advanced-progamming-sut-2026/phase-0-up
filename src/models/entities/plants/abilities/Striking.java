package models.entities.plants.abilities;

// An ability that hits something AT A DISTANCE without a projectile.
//
// Caulipower and Electric Blueberry pick a zombie anywhere on the board and damage it outright; Grave
// Buster destroys the grave under its own feet. Nothing flies, so there is no Projectile for the view
// to draw -- and all three ship their own effect animation in the dump, which had nothing to attach to.
//
// So the ability records WHERE it last struck and HOW MANY times it has. Purely informational, exactly
// like Plant.isWindingUp(): the counter is what the view watches for a rising edge, and the position is
// where it sends the effect. Nothing here decides anything.
public interface Striking {

    // Increments once per strike. The view compares it frame to frame; the absolute value means nothing.
    int strikeCount();

    // Board coordinates of the last strike, in the same space as Plant.getX()/getY().
    double strikeX();

    double strikeY();
}
