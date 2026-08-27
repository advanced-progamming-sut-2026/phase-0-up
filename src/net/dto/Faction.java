package net.dto;

// Which side of the lawn a player is playing in a two-player match.
//
// Lives in net/ rather than in models/ on purpose: it is part of the wire vocabulary (MatchStart tells
// each client which one it is, MatchOver names the winner) and both the client and the server have to
// hold it without either dragging the other's world in.
public enum Faction {
    PLANTS,
    ZOMBIES;

    public Faction opposite() {
        return this == PLANTS ? ZOMBIES : PLANTS;
    }
}
