package models.social;

// The three families of in-game reaction the spec asks for. The wire carries a kind plus an index
// (0..2) into a fixed catalogue -- never free text -- so an arbitrary string is structurally
// impossible to send to an opponent.
//
// In models/ rather than in net/ because the catalogue itself is a model (see Reaction), and models
// may not import net.. -- MvcBoundaryTest.modelsAreFreeOfTheNetwork. net/ may depend on models/, so
// the packets keep naming this one and nothing has to be translated at the boundary.
public enum ReactionKind {
    TEXT,
    EMOJI,
    STICKER
}
