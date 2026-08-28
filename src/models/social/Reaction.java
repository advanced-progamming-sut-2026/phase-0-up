package models.social;

// The nine things one player can say to the other during a match.
//
// Three lines of text, three emojis and three stickers, exactly as the spec lists them, and nothing
// else: the wire carries a (kind, index) pair and this enum is what that pair means. A player cannot
// send an arbitrary string because there is nowhere to put one -- which is a stronger guarantee than
// filtering would be, and it is the reason the protocol was shaped this way rather than as a chat.
//
// ## Why this is a model
//
// Both front ends have to name the same nine things, the server has to be able to check an index it
// is asked to relay, and none of that involves drawing. What each one LOOKS like is a view decision
// and lives in views.gdx.ui.ReactionArt; what each one IS lives here.
//
// The text is here rather than there because it is content, not presentation -- the terminal build
// would print the same sentence -- and because a text reaction with no text is not a thing.
public enum Reaction {

    // Deliberately good-natured. These go to a stranger, they cannot be turned off by the person
    // receiving them, and there are only three -- so all three are things you would not mind being
    // sent. "Is that all you've got?" is as sharp as this gets, and it is aimed at the lawn.
    GOOD_GAME(ReactionKind.TEXT, 0, "Good game!"),
    NICE_TRY(ReactionKind.TEXT, 1, "Nice try!"),
    IS_THAT_ALL(ReactionKind.TEXT, 2, "Is that all you've got?"),

    // Drawn from the game's own art, never as characters. The six bundled TTFs are game fonts with no
    // emoji glyphs in them, so an emoji rendered as TEXT is a row of tofu boxes -- see ReactionArt.
    SUNNY(ReactionKind.EMOJI, 0, null),
    BRAINZ(ReactionKind.EMOJI, 1, null),
    GRR(ReactionKind.EMOJI, 2, null),

    // Animations rather than stills. See ReactionArt for which clips, and why the choice was verified
    // against the dump rather than guessed from the names.
    TAUNT(ReactionKind.STICKER, 0, null),
    CHEER(ReactionKind.STICKER, 1, null),
    SULK(ReactionKind.STICKER, 2, null);

    public static final int PER_KIND = 3;

    private final ReactionKind kind;
    private final int index;
    private final String text;

    Reaction(ReactionKind kind, int index, String text) {
        this.kind = kind;
        this.index = index;
        this.text = text;
    }

    public ReactionKind kind() {
        return kind;
    }

    public int index() {
        return index;
    }

    // What a TEXT reaction says, or null for the other two kinds.
    public String text() {
        return text;
    }

    // The reaction a (kind, index) pair names, or null if it names none.
    //
    // Null rather than an exception, and the server relies on it: the pair arrives off a socket, so a
    // client that sent a nonsense index is asking a question this has to be able to answer with "no".
    public static Reaction of(ReactionKind kind, int index) {
        if (kind == null) {
            return null;
        }
        for (Reaction reaction : values()) {
            if (reaction.kind == kind && reaction.index == index) {
                return reaction;
            }
        }
        return null;
    }

    // The three of one kind, in the order they are shown.
    public static Reaction[] of(ReactionKind kind) {
        Reaction[] row = new Reaction[PER_KIND];
        for (int i = 0; i < PER_KIND; i++) {
            row[i] = of(kind, i);
        }
        return row;
    }
}
