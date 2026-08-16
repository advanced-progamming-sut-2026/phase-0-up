package models.entities.plants.abilities;

// An ability whose action comes in more than one form, drawn from a different clip each time.
//
// Kernel-pult is the case that needed it: it lobs a kernel or, on a roll, stunning butter, and the art
// ships a separate swing for each ("attack" and "attack2"). Which one was thrown is decided inside
// execute() and nothing else can know it.
//
// Informational, like Plant.isWindingUp(). Zero is the ordinary action; the view appends the number to
// the clip name for anything above it, so a third form would need no change here.
public interface VariantAction {

    int actionVariant();
}
