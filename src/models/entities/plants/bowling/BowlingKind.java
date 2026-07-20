package models.entities.plants.bowling;

// The three kinds of bowling nut in the Wall-nut Bowling mini-game, each with its own collision rule:
//   BOWLING -> damages a zombie then turns 45 degrees (90 off the top/bottom wall);
//   EXPLODE -> detonates a 3x3 blast on the first zombie it meets;
//   GIANT   -> crushes (instantly kills) any zombie it rolls over and keeps going straight.
public enum BowlingKind {
    BOWLING,
    EXPLODE,
    GIANT
}
