package views.renderers;

import models.game.GameSession;

// Draws the board.
//
// Only two entry points, because only two are ever called from outside: the whole lawn on its own, and
// the lawn preceded by the session header (wave, sun, plant food, mode status). The per-entity listings
// the terminal build prints underneath are its own business and live in ConsoleMapRenderer.
//
// The graphical build draws the board continuously from GameRenderer rather than on demand, so
// GdxMapRenderer only has to acknowledge the request -- see the note there.
public interface MapRenderer {
    void renderAllTheMap(GameSession activeSession);

    void renderGameSession(GameSession activeSession);
}
