package models.game.gamemodes;

import models.entities.zombies.Zombie;
import models.game.GameSession;
import utils.Result;

import java.util.Map;

// A lawn played from the zombies' side: five brains where the mowers would be, a roster of zombies
// bought with sun, and a red line they may not be summoned left of.
//
// Two modes are that lawn -- IZombieMode (the single-player mini-game, plants placed by the game) and
// VersusIZombieMode (the two-player match, plants placed by a person) -- and the views cannot tell
// them apart, because from the drawing side there is nothing to tell apart: the brains, the red line,
// the roster panel and the disco sun-makers look and behave identically in both.
//
// This interface exists so they do not have to. Before it, six view sites asked `instanceof IZombieMode`
// and every one of them would have silently drawn NOTHING in a versus match -- no brains at the lane
// ends, no red line, an empty roster panel -- while the model underneath worked perfectly. That is the
// worst shape of bug this project has: it compiles, it runs, and the thing you are playing is invisible.
//
// Deliberately NOT satisfied by making VersusIZombieMode extend IZombieMode. GameSession.minigameName()
// (:379) dispatches on `instanceof IZombieMode` and would credit a versus win as a single-player
// "I, Zombie" clear on whichever profile the session happened to hold.
public interface BrainLawn {

    // Which zombies may be summoned this game, and what each costs in sun. Insertion-ordered: the HUD
    // draws the roster panel in exactly this order.
    Map<String, Integer> getRoster();

    // Zombies may only be summoned from this column rightward. ModeOverlayRenderer draws it as the red
    // line; summonZombie is what actually enforces it.
    int getRedLineColumn();

    int brainsTotal();

    int brainsEaten();

    boolean isBrainEaten(int lane);

    // Which zombies are this lawn's sun makers -- they are otherwise ordinary bucketheads, so only the
    // mode can say. The view draws these as the disco mech instead.
    boolean isSunProducer(Zombie zombie);

    boolean isSummonable(String alias);

    // The zombie player's one action. Every refusal comes back as a Result the player can read, because
    // the same string is what the terminal prints and what the GUI raises as a toast.
    Result summonZombie(GameSession session, String type, int x, int y);
}
