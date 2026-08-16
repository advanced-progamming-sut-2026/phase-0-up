package views.gdx.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import controllers.systems.LeaderboardSystem;
import models.leaderboard.LbColumn;
import models.leaderboard.LeaderboardEntry;
import models.user.User;
import utils.storage.DatabaseManager;
import views.gdx.core.GdxContext;
import views.gdx.ui.MenuStyles;

import java.util.List;

// Where everybody stands.
//
// The board is READ, not commanded: LeaderboardSystem.sortBy snapshots the user roster into plain rows
// and sorts a fresh copy, so there is nothing to mutate and nothing to keep in step. The terminal's
// "leaderboard sort -c score -o desc" exists because a prompt has no column to click; this screen has
// the columns, so it calls the same system method directly rather than round-tripping a string through
// the router and back out through a renderer that would only have to hand it here anyway.
//
// Clicking a header sorts by it. Clicking the header that is already active flips the direction, which
// is the behaviour every table in every application has and the one thing a player will try first.
public final class LeaderboardScreen extends MenuScreen {

    // Columns, left to right, with the width each gets. Username is the row's identity and takes the
    // slack; the rest are numbers and need only enough room for their own headings.
    private static final float RANK_WIDTH = 62f;
    private static final float NAME_WIDTH = 220f;
    private static final float COLUMN_WIDTH = 152f;
    private static final float LIST_WIDTH = RANK_WIDTH + NAME_WIDTH + COLUMN_WIDTH * 5 + 24f;
    // A ceiling, not a size: the pane takes its content's height up to this, so a board with five
    // players is a five-row panel rather than five rows adrift in a screen of empty felt.
    private static final float LIST_MAX_HEIGHT = 400f;
    private static final float ROW_HEIGHT = 38f;
    // Two lines' worth. "Non-Daily Quests" does not fit one line at any font size that is still
    // readable, so the headings wrap instead of being abbreviated -- abbreviating them here would put
    // a second set of column names in the codebase, next to LbColumn's own. The column is wide enough
    // that the wrap falls between words, which is what stops "Mini-games" breaking as "Mini-game / s".
    private static final float HEADER_HEIGHT = 62f;
    private static final float HEADER_FONT = 0.6f;

    // Descending on Meow Points is what a leaderboard means by default -- the best score at the top.
    private static final LbColumn DEFAULT_COLUMN = LbColumn.MEOW_POINT;
    private static final boolean DEFAULT_ASCENDING = false;

    private static final Color HEADER = new Color(1f, 0.94f, 0.62f, 1f);
    private static final Color HEADER_IDLE = new Color(0.78f, 0.76f, 0.72f, 1f);
    private static final Color ROW_EVEN = new Color(0f, 0f, 0f, 0.32f);
    private static final Color ROW_ODD = new Color(0f, 0f, 0f, 0.18f);
    // The signed-in player's own row, so they can find themselves without reading every name.
    private static final Color ROW_SELF = new Color(0.20f, 0.34f, 0.16f, 0.75f);
    private static final Color MEDAL_GOLD = new Color(1f, 0.85f, 0.35f, 1f);
    private static final Color MEDAL_SILVER = new Color(0.85f, 0.87f, 0.92f, 1f);
    private static final Color MEDAL_BRONZE = new Color(0.86f, 0.60f, 0.36f, 1f);

    private final LeaderboardSystem leaderboard = LeaderboardSystem.getInstance();

    private Table header;
    private Table rows;
    private Label caption;

    private LbColumn column = DEFAULT_COLUMN;
    private boolean ascending = DEFAULT_ASCENDING;

    public LeaderboardScreen(GdxContext context) {
        super(context);
    }

    @Override
    protected void build(Table root) {
        Table panel = MenuPanel.build(skin, "Leaderboard");

        caption = MenuStyles.label(skin, "", MenuStyles.TEXT);
        panel.add(caption).padBottom(10f).row();

        header = new Table();
        panel.add(header).width(LIST_WIDTH).padBottom(4f).row();

        rows = new Table();
        rows.top();
        ScrollPane pane = new ScrollPane(rows, skin);
        pane.setScrollingDisabled(true, false);
        pane.setFadeScrollBars(false);
        pane.setOverscroll(false, false);
        panel.add(pane).width(LIST_WIDTH).maxHeight(LIST_MAX_HEIGHT).padBottom(14f).row();

        TextButton back = MenuStyles.button(skin, "Back", MenuStyles.BUTTON_PURPLE);
        back.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                goBack();
            }
        });
        panel.add(back).width(200f).height(56f).row();

        root.setFillParent(true);
        root.add(panel);

        rebuild();
    }

    private void rebuild() {
        buildHeader();
        buildRows();
    }

    private void buildHeader() {
        header.clearChildren();
        header.add(label("#")).width(RANK_WIDTH).height(HEADER_HEIGHT);
        header.add(label("Player")).width(NAME_WIDTH).height(HEADER_HEIGHT);
        for (LbColumn candidate : LbColumn.values()) {
            header.add(headerCell(candidate)).width(COLUMN_WIDTH).height(HEADER_HEIGHT).padLeft(2f);
        }
    }

    // "#" and "Player" are not buttons: rank IS whatever the sort says it is, and the model has no
    // comparator for the username on its own.
    private Label label(String text) {
        Label label = MenuStyles.label(skin, text, MenuStyles.TEXT);
        label.setColor(HEADER_IDLE);
        return label;
    }

    // A sortable column heading.
    //
    // Which column is sorting is carried by the BUTTON, not by a caret in its text: green for the
    // active one, brown for the rest, the same green/brown pairing every other screen uses for "this
    // one" versus "the others". A glyph would have to fit inside "Non-Daily Quests" in a 138px button,
    // which it does not -- the first attempt spilled the arrow out past the panel's edge. Which
    // DIRECTION it is sorting is in the caption above, where there is room to say it in words.
    private Actor headerCell(LbColumn target) {
        boolean active = target == column;
        TextButton button = MenuStyles.button(skin, target.getDisplayName(),
                active ? MenuStyles.BUTTON_GREEN_SMALL : MenuStyles.BUTTON_BROWN);
        button.getLabel().setWrap(true);
        button.getLabel().setFontScale(HEADER_FONT);
        button.getLabel().setColor(active ? HEADER : HEADER_IDLE);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                sortBy(target);
            }
        });
        return button;
    }

    // Clicking the active column flips it; clicking another switches to it. A fresh column starts
    // descending because every column here is "more is better", and ascending would open on the
    // players doing worst.
    private void sortBy(LbColumn target) {
        if (target == column) {
            ascending = !ascending;
        } else {
            column = target;
            ascending = false;
        }
        rebuild();
    }

    private void buildRows() {
        rows.clearChildren();
        List<LeaderboardEntry> entries =
                leaderboard.sortBy(column, ascending, DatabaseManager.getInstance());
        caption.setText(captionFor(entries.size()));

        if (entries.isEmpty()) {
            rows.add(MenuStyles.label(skin, "Nobody on the board yet. Be the first!", MenuStyles.TEXT))
                    .pad(30f).row();
            return;
        }
        String me = currentUsername();
        for (int i = 0; i < entries.size(); i++) {
            rows.add(row(entries.get(i), i + 1, me)).width(LIST_WIDTH - 24f).height(ROW_HEIGHT).row();
        }
    }

    private Table row(LeaderboardEntry entry, int rank, String me) {
        boolean self = me != null && me.equalsIgnoreCase(entry.getUsername());

        Table row = new Table();
        row.setBackground(context.assets().solid(
                self ? ROW_SELF : (rank % 2 == 0 ? ROW_EVEN : ROW_ODD)));

        Label rankLabel = MenuStyles.label(skin, String.valueOf(rank), MenuStyles.TEXT);
        rankLabel.setColor(medal(rank));
        row.add(rankLabel).width(RANK_WIDTH);

        Label name = MenuStyles.label(skin, entry.getUsername(), MenuStyles.TEXT);
        name.setAlignment(Align.left);
        row.add(name).width(NAME_WIDTH).left();

        // Same order as the headings, and read through the same enum, so a column added to LbColumn
        // shows up in both places or in neither.
        for (LbColumn candidate : LbColumn.values()) {
            row.add(MenuStyles.label(skin, valueOf(entry, candidate), MenuStyles.TEXT))
                    .width(COLUMN_WIDTH);
        }
        return row;
    }

    // Rank colour: the podium gets one, everybody else is plain. Rank comes from the ORDER, not from
    // the score, so it means "third by this column" and changes when the column does.
    private Color medal(int rank) {
        return switch (rank) {
            case 1 -> MEDAL_GOLD;
            case 2 -> MEDAL_SILVER;
            case 3 -> MEDAL_BRONZE;
            default -> Color.WHITE;
        };
    }

    // LEVEL is the odd one out: it sorts on chapter-then-level but reads as the stage label the entry
    // already knows how to build ("1-3"), which is one behind the next-unlocked pointer.
    private String valueOf(LeaderboardEntry entry, LbColumn candidate) {
        return switch (candidate) {
            case LEVEL -> entry.getStageLabel();
            case MINIGAMES -> String.valueOf(entry.getMinigamesCompleted());
            case DAILY_QUESTS -> String.valueOf(entry.getDailyQuests());
            case NONDAILY_QUESTS -> String.valueOf(entry.getNonDailyQuests());
            case MEOW_POINT -> String.valueOf(entry.getBestMeowPoint());
        };
    }

    private String captionFor(int players) {
        String direction = ascending ? "lowest first" : "highest first";
        return players + (players == 1 ? " player" : " players")
                + "  -  sorted by " + column.getDisplayName() + ", " + direction;
    }

    private String currentUsername() {
        User user = context.appSession().getCurrentUser();
        return user == null ? null : user.getUsername();
    }
}
