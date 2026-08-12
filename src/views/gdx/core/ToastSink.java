package views.gdx.core;

import utils.Result;

// Where the graphical renderers send their text.
//
// Extracted from Toasts so the seventeen Gdx*Renderer implementations depend on this and not on a
// class that owns a Stage, a Skin and a font: with the sink behind an interface, every one of them can
// be constructed and asserted against in a plain JUnit test, with no GL context and no window. Toasts
// is the only production implementation and already had exactly these four methods.
public interface ToastSink {

    // Routes a Result by its own success flag, so callers never have to branch. This is what the
    // Gdx*Renderer implementations call for practically everything.
    void show(Result result);

    void info(String message);

    void success(String message);

    void error(String message);
}
