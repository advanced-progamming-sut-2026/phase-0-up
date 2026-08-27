package net.dto;

// One buyable card and what it costs in sun.
//
// Serves BOTH sides of a versus match -- the plant player's seed bank and the zombie player's roster --
// because they are the same shape and the view already draws them with the same widget: GameHud builds
// an I, Zombie roster card out of a SeedPacket and a price, exactly as it builds a plant card.
//
// A LIST of these, not a Map<String,Integer>. Order is what the player sees along the seed bar, and a
// map's order is an accident of whichever Map implementation Gson happened to deserialise into.
public record CardOffer(String type, int cost) {
}
