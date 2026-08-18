package views.gdx.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;
import controllers.systems.game.QuestSystem;
import models.quests.Quest;
import models.quests.QuestPriority;
import models.quests.QuestProgress;
import models.quests.Reward.CurrencyReward;
import models.quests.Reward.InventoryReward;
import models.quests.Reward.Reward;
import models.quests.Reward.UnlockableReward;
import models.user.Profile;
import models.user.User;
import views.gdx.core.GdxContext;
import views.gdx.ui.MenuStyles;

import java.util.List;

// The Travel Log: every quest the game is keeping score of, ranked, plus the mini-games.
//
// The quest half is READ, not commanded. QuestSystem builds the list from the registry and flags each
// one complete off the profile, exactly as ShowTravelLogPageCommand asks it to for the prompt; this
// screen draws the same list. There is nothing to mutate -- a quest completes in play and claims itself,
// so the log has no button that changes anything.
//
// The mini-game half is the exception, and it does NOT post "travel log play <game>". That command
// routes to InputRouter.launchMinigame, which for four of the five ends in GameEngine.startLoop() --
// the terminal's blocking loop, which on the render thread freezes the window with no error. So the
// launch is rebuilt here from the same parts (MinigameFactory + a GameSession on the AppSession), which
// is what SeedSelectionScreen already does for an adventure level.
public final class TravelLogScreen extends MenuScreen {

    private enum Page { ALL, MAIN, DAILY, EPIC, MINIGAMES }

    private static final float LIST_WIDTH = 960f;
    private static final float LIST_HEIGHT = 452f;
    private static final float ROW_PAD = 8f;
    private static final float ICON_SIZE = 46f;
    private static final float REWARD_WIDTH = 210f;
    private static final float BAR_WIDTH = 260f;
    // The coloured strip down a row's left edge, which is how priority is shown. A word saying
    // "CRITICAL" costs a line of text per quest and repeats what the ordering already says.
    private static final float RANK_STRIP = 5f;

    private static final Color DIM = new Color(0.78f, 0.76f, 0.72f, 1f);
    private static final Color ROW_FACE = new Color(0f, 0f, 0f, 0.32f);
    private static final Color ROW_DONE = new Color(0.16f, 0.26f, 0.11f, 0.62f);
    private static final Color DONE_TEXT = new Color(0.72f, 1f, 0.62f, 1f);
    private static final Color REWARD_TEXT = new Color(1f, 0.88f, 0.45f, 1f);

    // One per priority tier, brightest at the top. QuestPriority is declared CRITICAL, HIGH, MEDIUM,
    // LOW and the list is sorted by that ordinal, so the strips also run bright-to-dim down the page.
    private static final Color RANK_CRITICAL = new Color(0.95f, 0.42f, 0.30f, 1f);
    private static final Color RANK_HIGH = new Color(0.78f, 0.55f, 0.95f, 1f);
    private static final Color RANK_MEDIUM = new Color(0.55f, 0.82f, 0.45f, 1f);
    private static final Color RANK_LOW = new Color(0.52f, 0.50f, 0.47f, 1f);

    // The real game's own quest art. The three category icons are the tab icons from PvZ2's quest
    // panel, so a Main row and a Daily row are told apart without reading either.
    private static final String ICON_MAIN = "image_ui_quests_achievements_active";
    private static final String ICON_DAILY = "image_ui_quests_daily_active";
    private static final String ICON_EPIC = "image_ui_quests_epic_active";
    private static final String ICON_COINS = "image_ui_quests_epic_reward_coins";
    private static final String ICON_GEMS = "image_ui_quests_epic_reward_gems";
    private static final String ICON_PACKETS = "image_ui_quests_epic_reward_pinata";
    private static final String ICON_UNLOCK = "image_ui_quests_questicons_plant";
    // Done and not-done, as a green tick against a dead grey disc.
    //
    // Not the almanac's checkbox_enabled/_disabled, which are the bright and greyed states of one TICKED
    // box -- see the note in MenuStyles. checkbox_on/_off ARE a real pair (a gold disc and a grey one),
    // but gold-against-grey is a far weaker signal than a tick, and this row has to answer one question.
    private static final String CHECK_ON = "image_ui_generic_check_mark_sm";
    private static final String CHECK_OFF = "checkbox_off";

    // Stateless as far as the log is concerned: getQuestsForPage reads the registry and the profile,
    // and the per-level tally this also owns is never touched here.
    private final QuestSystem quests = new QuestSystem();

    private final java.util.EnumMap<Page, TextButton> tabs = new java.util.EnumMap<>(Page.class);

    private Table list;
    private Label summary;

    // Which difficulty a mini-game launches at. One picker for the page rather than one per tile: it is
    // the command's single -d argument, and five copies of the same control is five things to keep in
    // step.
    private int difficulty = 1;

    private Page page = startingPage();

    public TravelLogScreen(GdxContext context) {
        super(context);
    }

    // -Dpvz.tab=minigames opens straight onto the mini-game tiles. Same reason the almanac has it: a
    // page that is one click in is invisible to an unattended screenshot run.
    private static Page startingPage() {
        String wanted = views.gdx.core.DebugFlags.START_TAB;
        for (Page candidate : Page.values()) {
            if (candidate.name().equalsIgnoreCase(wanted)) {
                return candidate;
            }
        }
        return Page.ALL;
    }

    @Override
    protected void build(Table root) {
        Table panel = MenuPanel.build(skin, "Travel Log");

        Table head = new Table();
        head.add(tabs()).left();
        head.add().expandX();
        summary = subtitle("");
        head.add(summary).right();
        panel.add(head).width(LIST_WIDTH).padBottom(10f).row();

        list = new Table();
        list.top();
        ScrollPane pane = new ScrollPane(list, skin);
        pane.setScrollingDisabled(true, false);
        pane.setFadeScrollBars(false);
        pane.setOverscroll(false, false);
        panel.add(pane).width(LIST_WIDTH).height(LIST_HEIGHT).padBottom(14f).row();

        TextButton back = MenuStyles.button(skin, "Back", MenuStyles.BUTTON_PURPLE);
        back.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                goBack();
            }
        });
        panel.add(back).width(200f).height(54f).row();

        root.setFillParent(true);
        root.add(panel);

        rebuild();
    }

    private Table tabs() {
        Table row = new Table();
        addTab(row, Page.ALL, "All");
        addTab(row, Page.MAIN, "Main");
        addTab(row, Page.DAILY, "Daily");
        addTab(row, Page.EPIC, "Epic");
        addTab(row, Page.MINIGAMES, "Mini-games");
        return row;
    }

    private void addTab(Table row, Page target, String text) {
        TextButton button = MenuStyles.button(skin, text, MenuStyles.BUTTON_BROWN);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                page = target;
                rebuild();
            }
        });
        tabs.put(target, button);
        row.add(button).width(target == Page.MINIGAMES ? 168f : 116f).height(46f).padRight(6f);
    }

    private void rebuild() {
        for (java.util.Map.Entry<Page, TextButton> entry : tabs.entrySet()) {
            entry.getValue().setStyle(skin.get(entry.getKey() == page
                    ? MenuStyles.BUTTON_GREEN : MenuStyles.BUTTON_BROWN,
                    TextButton.TextButtonStyle.class));
        }
        list.clearChildren();
        if (page == Page.MINIGAMES) {
            buildMinigames();
        } else {
            buildQuests();
        }
    }

    // ---- quest pages ----------------------------------------------------------------------------

    private void buildQuests() {
        Profile profile = profile();
        List<Quest> page = questsForPage(profile);
        summary.setText(summaryLine(page));
        if (page.isEmpty()) {
            list.add(MenuStyles.label(skin, "Nothing filed on this page yet.", MenuStyles.TEXT))
                    .pad(40f).row();
            return;
        }
        for (Quest quest : page) {
            list.add(questRow(quest, profile)).width(LIST_WIDTH - 24f).padBottom(ROW_PAD).row();
        }
    }

    private List<Quest> questsForPage(Profile profile) {
        return switch (page) {
            case MAIN -> quests.getQuestsForPage(Quest.Category.MAIN, profile);
            case DAILY -> quests.getQuestsForPage(Quest.Category.DAILY, profile);
            case EPIC -> quests.getQuestsForPage(Quest.Category.EPIC, profile);
            default -> quests.getSortedQuestsForLog(profile);
        };
    }

    private String summaryLine(List<Quest> page) {
        int done = 0;
        for (Quest quest : page) {
            if (quest.isComplete()) {
                done++;
            }
        }
        return done + " of " + page.size() + " done -- most important first.";
    }

    private Table questRow(Quest quest, Profile profile) {
        boolean done = quest.isComplete();

        Table row = new Table();
        row.setBackground(context.assets().solid(done ? ROW_DONE : ROW_FACE));

        Table strip = new Table();
        strip.setBackground(context.assets().solid(rankColour(quest.getPriority())));
        row.add(strip).width(RANK_STRIP).growY();

        row.add(icon(categoryIcon(quest.getCategory()))).size(ICON_SIZE).pad(10f, 12f, 10f, 12f).top();
        row.add(questText(quest, profile, done)).growX().pad(10f, 0f, 10f, 12f).top();
        row.add(rewardBlock(quest.getReward())).width(REWARD_WIDTH).pad(10f, 0f, 10f, 12f).top();
        return row;
    }

    private Table questText(Quest quest, Profile profile, boolean done) {
        Table box = new Table();

        Label name = MenuStyles.label(skin, quest.getName() + (done ? "  -  done!" : ""),
                MenuStyles.HEADING);
        name.setAlignment(Align.left);
        if (done) {
            name.setColor(DONE_TEXT);
        }
        box.add(name).growX().left().row();

        Label detail = MenuStyles.label(skin, quest.getDescription(), MenuStyles.TEXT);
        detail.setWrap(true);
        detail.setAlignment(Align.left);
        detail.setColor(DIM);
        detail.setFontScale(0.8f);
        box.add(detail).growX().left().padTop(2f).row();

        Table goal = goalRow(quest, profile, done);
        if (goal != null) {
            box.add(goal).growX().left().padTop(6f).row();
        }
        return box;
    }

    // How far along the quest is -- and the one place the two kinds of goal part company.
    //
    // A cross-level quest accumulates on the profile, so "12 / 20" is a real running total and gets a
    // bar. A single-level quest has no running total to show: its progress is 0 until a level ends and
    // the whole thing either happened inside that one match or did not. A bar there would promise a
    // carry-over that does not exist, so it gets a tick box instead. That is QuestProgress.crossLevel,
    // and it is the reason the record carries the flag at all.
    private Table goalRow(Quest quest, Profile profile, boolean done) {
        QuestProgress progress = profile == null ? null : quest.getProgress(profile);
        if (progress == null || !progress.isMeasurable()) {
            return null;
        }
        return progress.crossLevel() ? progressBar(progress) : checkbox(progress, done);
    }

    private Table progressBar(QuestProgress progress) {
        int shown = Math.min(progress.current(), progress.target());

        ProgressBar bar = new ProgressBar(0f, progress.target(), 1f, false, skin);
        bar.setValue(shown);
        bar.setAnimateDuration(0.25f);

        Label count = MenuStyles.label(skin, shown + " / " + progress.target(), MenuStyles.TEXT);
        count.setFontScale(0.78f);
        count.setColor(shown >= progress.target() ? DONE_TEXT : DIM);

        // left(), or the cell's growX centres the bar under a left-aligned description and the row reads
        // as belonging to nothing.
        Table row = new Table();
        row.left();
        row.add(bar).width(BAR_WIDTH).height(18f).padRight(10f);
        row.add(count).left();
        return row;
    }

    private Table checkbox(QuestProgress progress, boolean done) {
        Label text = MenuStyles.label(skin,
                "All " + progress.target() + " in a single level", MenuStyles.TEXT);
        text.setAlignment(Align.left);
        text.setFontScale(0.78f);
        text.setColor(done ? DONE_TEXT : DIM);

        Table row = new Table();
        row.left();
        row.add(icon(done ? CHECK_ON : CHECK_OFF)).size(20f).padRight(8f);
        row.add(text).left();
        return row;
    }

    private Table rewardBlock(Reward reward) {
        Label text = MenuStyles.label(skin, reward.describe(), MenuStyles.TEXT);
        text.setWrap(true);
        text.setAlignment(Align.left);
        text.setColor(REWARD_TEXT);
        text.setFontScale(0.82f);

        Table box = new Table();
        box.add(icon(rewardIcon(reward))).size(34f).padRight(8f).top();
        box.add(text).growX().left().top();
        return box;
    }

    // ---- mini-games -----------------------------------------------------------------------------

    private void buildMinigames() {
        summary.setText("Five sides of the lawn you do not get in the campaign.");
        list.add(difficultyRow()).width(LIST_WIDTH - 24f).padBottom(12f).row();

        Table grid = new Table();
        int column = 0;
        for (Minigame game : Minigame.values()) {
            grid.add(minigameTile(game)).size(296f, 176f).pad(6f);
            if (++column % 3 == 0) {
                grid.row();
            }
        }
        list.add(grid).row();
    }

    private Table difficultyRow() {
        views.gdx.ui.Cycler picker = new views.gdx.ui.Cycler(skin,
                new String[] {"1", "2", "3", "4", "5"}, difficulty - 1);
        picker.onChange(index -> difficulty = index + 1);

        Table row = new Table();
        row.add(subtitle("Difficulty")).padRight(12f);
        row.add(picker);
        row.add().expandX();
        return row;
    }

    private Table minigameTile(Minigame game) {
        Table tile = new Table();
        tile.setBackground(context.assets().solid(ROW_FACE));
        tile.pad(12f);

        Label name = MenuStyles.label(skin, game.label, MenuStyles.HEADING);
        tile.add(name).growX().padBottom(4f).row();

        Label blurb = MenuStyles.label(skin, game.blurb, MenuStyles.TEXT);
        blurb.setWrap(true);
        blurb.setAlignment(Align.center);
        blurb.setColor(DIM);
        blurb.setFontScale(0.78f);
        tile.add(blurb).growX().growY().row();

        TextButton play = MenuStyles.button(skin, "Play", MenuStyles.BUTTON_GREEN);
        play.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                launch(game);
            }
        });
        tile.add(play).width(180f).height(48f).padTop(6f).row();
        return tile;
    }

    // Starts a mini-game the way SeedSelectionScreen starts a level: build the Level, hang a GameSession
    // on the AppSession, and move the menu. GameScreen picks the session up from there and drives the
    // engine from the render loop.
    //
    // Zombotany goes to seed selection first, because it is an ordinary level whose zombies happen to be
    // plants -- exactly the split InputRouter.launchMinigame already makes.
    private void launch(Minigame game) {
        User user = context.appSession().getCurrentUser();
        if (user == null) {
            context.toasts().error("Sign in first -- a mini-game needs somewhere to put the score.");
            return;
        }
        models.game.GameSession session =
                new models.game.GameSession(user.getProfile(), game.build(difficulty));
        context.appSession().setCurrentGameSession(session);
        context.renderers().travelLog().launchingMinigame(game.label, difficulty);
        context.appSession().setCurrentMenu(game.picksSeeds
                ? controllers.engine.MenuType.PLANTS_MENU : controllers.engine.MenuType.IN_GAME);
    }

    // The five mini-games the Travel Log hosts, in the order ShowTravelLogPageCommand lists them.
    private enum Minigame {
        VASEBREAKER("Vasebreaker", "Smash the vases. Some hold a plant, some hold a zombie.", false),
        IZOMBIE("I, Zombie", "Play the other side: buy zombies and eat the brains.", false),
        BOWLING("Wall-nut Bowling", "Roll Wall-nuts down the lane and mind the ricochet.", false),
        BEGHOULED("Beghouled", "Swap plants in threes until the lawn does the fighting.", false),
        ZOMBOTANY("Zombotany", "The zombies are plants. Pick your own and hold the line.", true);

        private final String label;
        private final String blurb;
        // Whether the mini-game routes through seed selection before the lawn.
        private final boolean picksSeeds;

        Minigame(String label, String blurb, boolean picksSeeds) {
            this.label = label;
            this.blurb = blurb;
            this.picksSeeds = picksSeeds;
        }

        models.game.Level build(int difficulty) {
            return switch (this) {
                case VASEBREAKER -> factories.MinigameFactory.createVasebreaker(difficulty);
                case IZOMBIE -> factories.MinigameFactory.createIZombie(difficulty);
                case BOWLING -> factories.MinigameFactory.createWallnutBowling(difficulty);
                case BEGHOULED -> factories.MinigameFactory.createBeghouled(difficulty);
                case ZOMBOTANY -> factories.MinigameFactory.createZombotany(difficulty);
            };
        }
    }

    // ---- small parts ----------------------------------------------------------------------------

    private static String categoryIcon(Quest.Category category) {
        return switch (category) {
            case DAILY -> ICON_DAILY;
            case EPIC -> ICON_EPIC;
            default -> ICON_MAIN;
        };
    }

    // The reward's own shape decides the picture, so a quest that pays gems never shows a coin.
    private static String rewardIcon(Reward reward) {
        if (reward instanceof CurrencyReward currency) {
            return currency.getCurrency() == CurrencyReward.Currency.GEMS ? ICON_GEMS : ICON_COINS;
        }
        if (reward instanceof InventoryReward) {
            return ICON_PACKETS;
        }
        if (reward instanceof UnlockableReward) {
            return ICON_UNLOCK;
        }
        return null;
    }

    private static Color rankColour(QuestPriority priority) {
        return switch (priority) {
            case CRITICAL -> RANK_CRITICAL;
            case HIGH -> RANK_HIGH;
            case MEDIUM -> RANK_MEDIUM;
            case LOW -> RANK_LOW;
        };
    }

    private Actor icon(String id) {
        Drawable art = id == null ? null : MenuStyles.drawable(skin, id);
        return art == null ? new Table() : new Image(art);
    }

    private Label subtitle(String text) {
        Label label = MenuStyles.label(skin, text, MenuStyles.TEXT);
        label.setColor(DIM);
        label.setFontScale(0.8f);
        return label;
    }

    private Profile profile() {
        User user = context.appSession().getCurrentUser();
        return user == null ? null : user.getProfile();
    }
}
