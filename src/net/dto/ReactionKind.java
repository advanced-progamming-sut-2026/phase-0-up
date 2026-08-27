package net.dto;

// The three families of in-game reaction the spec asks for. The wire carries a kind plus an index
// (0..2) into a fixed catalogue -- never free text -- so an arbitrary string is structurally
// impossible to send to an opponent.
public enum ReactionKind {
    TEXT,
    EMOJI,
    STICKER
}
