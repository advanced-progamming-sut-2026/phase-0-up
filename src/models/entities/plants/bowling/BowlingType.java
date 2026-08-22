package models.entities.plants.bowling;

import models.entities.Entity;
import models.game.GameSession;

// A bowling nut rolling across the lawn. It carries its own continuous position (px, py) and heading
// so it can travel diagonally after a 45-degree ricochet -- Entity's integer (x, y) only mirror the
// tile it is currently over. The WallnutBowlingMode owns the physics (advancing, collisions, walls);
// this class just holds the state and the primitive moves.
public class BowlingType extends Entity {
    private final BowlingKind kind;
    private double px;          // continuous column position
    private double py;          // continuous row position (fractional while travelling diagonally)
    private double headingDeg;  // 0 = rolling right (+x); 90 = down (+y, toward higher row index)
    private double speed;
    private boolean finished;   // rolled off the board or spent (Explode-o-Nut) -> remove

    public BowlingType(String name, int id, double px, int py, BowlingKind kind) {
        super(name, id, px, py);
        this.kind = kind;
        this.px = px;
        this.py = py;
        this.headingDeg = 0.0;   // freshly bowled nuts roll straight toward the zombies
        this.speed = 0.5;
        this.finished = false;
    }

    @Override
    public void update(GameSession gameSession) {
        // Movement is driven by WallnutBowlingMode.onTick, which has the board context it needs.
    }

    // Steps the nut one tick along its heading.
    public void advance() {
        double rad = Math.toRadians(headingDeg);
        px += speed * Math.cos(rad);
        py += speed * Math.sin(rad);
        setX(px);
        setY((int) Math.round(py));
    }

    // The two headings a struck nut can leave on: down-and-forward, or up-and-forward.
    private static final double DEFLECT_DOWN = 45.0;
    private static final double DEFLECT_UP = 315.0;

    // Bounces off a zombie: 45 degrees, and ALWAYS still travelling toward the horde.
    //
    // This replaces rotate(45), which ACCUMULATED. Two hits put the nut at 90 -- straight down its own
    // column, hitting nothing -- and three at 135, rolling backwards up the lawn away from the house.
    // A bowling nut that retreats is not a ricochet, and the doc's rule is a 45-degree turn per strike,
    // not 45 degrees added to whatever it was doing.
    //
    // Which way it goes alternates, so a nut ploughing through a queue zig-zags between the lanes
    // instead of committing to one diagonal and leaving the board.
    public void deflect() {
        headingDeg = headingDeg > 0.0 && headingDeg < 180.0 ? DEFLECT_UP : DEFLECT_DOWN;
    }

    // Rotates the heading by the given signed degrees (kept in [0, 360)).
    public void rotate(double degrees) {
        headingDeg = (headingDeg + degrees) % 360.0;
        if (headingDeg < 0) {
            headingDeg += 360.0;
        }
    }

    // Reflects the heading across the horizontal (bounces off the top/bottom wall): the vertical
    // component flips while the horizontal one is kept. For a nut rolling at 45 degrees this is exactly
    // a 90-degree turn, and unlike a blind +90 it always points the nut back off the wall.
    public void reflectVertical() {
        headingDeg = (360.0 - headingDeg) % 360.0;
    }

    public BowlingKind getKind() { return kind; }
    public double getPx() { return px; }
    public double getPy() { return py; }
    public void setPy(double py) { this.py = py; setY((int) Math.round(py)); }
    public int getRow() { return (int) Math.round(py); }
    public double getHeadingDeg() { return headingDeg; }
    public double getSpeed() { return speed; }
    public boolean isFinished() { return finished; }
    public void finish() { this.finished = true; }
}
