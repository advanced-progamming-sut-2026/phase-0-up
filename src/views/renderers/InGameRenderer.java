package views.renderers;

import utils.Result;

// Where every in-game Command reports what it did.
//
// This is an interface rather than a class so the front end can be chosen at the composition root
// instead of being compiled into the Commands. The terminal build injects ConsoleInGameRenderer and
// the sentence is printed; the graphical build injects GdxInGameRenderer and the same sentence becomes
// a toast. Neither the Command nor the rule it enforced changes, which is why "not enough sun" reads
// identically whether the player typed the command or clicked the lawn.
public interface InGameRenderer {
    void render(Result result);
}
