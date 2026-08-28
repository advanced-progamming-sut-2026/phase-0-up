package views.gdx.bridge;

// What GameScreen needs from whatever is advancing the board.
//
// There are two of those now and they have almost nothing in common underneath. GameLoopDriver runs
// the real GameEngine off an accumulator; NetLoopDriver runs nothing at all and applies the server's
// snapshots instead. GameScreen does not care: it calls update() once a frame, asks alpha() for the
// interpolation blend, and asks isPlaying()/isPaused() to decide whether to freeze the animation clock
// and which overlay to raise.
//
// Extracted rather than added to, because "the loop" and "the simulation" turned out to be two ideas
// wearing one name. A networked client HAS a loop -- there is still a frame rate, still an alpha,
// still a board that stops -- and has no simulation whatsoever.
public interface LoopDriver {

    // One frame. deltaSeconds is real time; what a driver does with it is its own business.
    void update(float deltaSeconds);

    // How far through the current tick we are, 0..1. Renderers blend positions with it.
    float alpha();

    // Whether the board is still live. False once the level has been won, lost, or the match ended.
    boolean isPlaying();

    boolean isPaused();

    void setPaused(boolean paused);

    void togglePause();

    void setGameSpeed(int gameSpeed);

    int gameSpeed();

    // How many ticks this board has run, for the HUD clock and the wave bar.
    long ticksRun();
}
