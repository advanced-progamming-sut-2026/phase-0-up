package views.gdx.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import controllers.systems.NewsSystem;
import models.news.News;
import models.news.NewsType;
import models.user.Profile;
import models.user.User;
import views.gdx.core.GdxContext;
import views.gdx.ui.MenuStyles;

import java.util.ArrayList;
import java.util.List;

// The newspaper: every announcement the game has filed for this player.
//
// Reading the list is deliberately a READ-ONLY act. The terminal's "menu news show-unread" marks
// everything read as a side effect of printing it, which is fine at a prompt -- you asked for the
// unread ones, you got them, they are no longer new. On a screen it is not: a menu you opened by
// accident, or bounced off on the way somewhere else, would silently clear the badge and take the
// stories with it. So the list here is drawn straight off the Profile, and the only thing that marks
// anything read is the button that says so.
//
// That button posts the same command the terminal does, so what "read" means stays the model's answer.
public final class NewsScreen extends MenuScreen {

    private static final float LIST_WIDTH = 820f;
    private static final float LIST_HEIGHT = 430f;

    // An unread story's marker. Red, because it is the same signal as the main menu's count badge and
    // the player should not have to learn two.
    private static final float DOT_SIZE = 14f;

    private static final Color META = new Color(0.72f, 0.70f, 0.66f, 1f);
    private static final Color UNREAD_TITLE = new Color(1f, 0.94f, 0.62f, 1f);
    private static final Color ROW_UNREAD = new Color(0.24f, 0.20f, 0.10f, 0.55f);
    private static final Color ROW_READ = new Color(0f, 0f, 0f, 0.30f);

    private Table list;
    private Label counter;
    private TextButton filter;
    private TextButton markAll;

    // Whether the list is showing only what has not been read yet. Starts on ALL: a player who opens
    // the paper with nothing unread should see the archive, not an empty screen.
    private boolean unreadOnly;

    public NewsScreen(GdxContext context) {
        super(context);
    }

    @Override
    protected void build(Table root) {
        Table panel = MenuPanel.build(skin, "News");

        counter = MenuStyles.label(skin, "", MenuStyles.TEXT);
        panel.add(counter).padBottom(10f).row();

        panel.add(toolbar()).width(LIST_WIDTH).padBottom(10f).row();

        list = new Table();
        list.top();
        ScrollPane pane = new ScrollPane(list, skin);
        pane.setScrollingDisabled(true, false);
        pane.setFadeScrollBars(false);
        pane.setOverscroll(false, false);
        panel.add(pane).size(LIST_WIDTH, LIST_HEIGHT).padBottom(14f).row();

        panel.add(actions()).row();

        root.setFillParent(true);
        root.add(panel);

        rebuild();
    }

    private Table toolbar() {
        filter = MenuStyles.button(skin, "", MenuStyles.BUTTON_BROWN);
        filter.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                unreadOnly = !unreadOnly;
                rebuild();
            }
        });

        Table bar = new Table();
        bar.add(filter).width(220f).height(44f).left();
        bar.add().expandX();
        return bar;
    }

    private Table actions() {
        markAll = MenuStyles.button(skin, "Mark All Read", MenuStyles.BUTTON_GREEN);
        markAll.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                // The model's own command, not a loop over News.markAsRead here: it marks the whole
                // list AND saves, and doing it from the screen would be the second place that decides
                // what "read" means.
                commands.submit("menu news show-unread");
                rebuild();
            }
        });

        TextButton back = MenuStyles.button(skin, "Back", MenuStyles.BUTTON_PURPLE);
        back.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                goBack();
            }
        });

        Table actions = new Table();
        actions.add(markAll).width(240f).height(56f).padRight(12f);
        actions.add(back).width(180f).height(56f);
        return actions;
    }

    private void rebuild() {
        list.clearChildren();
        List<News> stories = stories();
        counter.setText(summary());
        filter.setText(unreadOnly ? "Showing: Unread" : "Showing: All");
        // Dimmed when there is nothing to mark, but NOT disabled. The skin's green button has no
        // disabled drawable, so setDisabled would leave a button that looks pressable and silently is
        // not; pressing this one always answers, because the command has its own "all caught up" line.
        markAll.getLabel().setColor(unreadCount() == 0 ? META : Color.WHITE);

        if (stories.isEmpty()) {
            list.add(MenuStyles.label(skin,
                    unreadOnly ? "Nothing unread. You are impressively up to date."
                            : "No news yet. Go make some headlines!", MenuStyles.TEXT))
                    .pad(30f).row();
            return;
        }
        for (News story : stories) {
            list.add(row(story)).width(LIST_WIDTH - 24f).padBottom(8f).row();
        }
    }

    // Newest first. The list is appended to in chronological order and news has no timestamp finer
    // than a date, so the id is the only thing that actually orders two stories filed the same day.
    private List<News> stories() {
        Profile profile = profile();
        List<News> all = profile == null || profile.getNewsList() == null
                ? List.of() : profile.getNewsList();
        List<News> shown = new ArrayList<>();
        for (News story : all) {
            if (story != null && (!unreadOnly || !story.isRead())) {
                shown.add(story);
            }
        }
        shown.sort((a, b) -> Integer.compare(b.getId(), a.getId()));
        return shown;
    }

    private Table row(News story) {
        boolean unread = !story.isRead();

        Table row = new Table();
        row.setBackground(context.assets().solid(unread ? ROW_UNREAD : ROW_READ));
        row.pad(10f, 12f, 10f, 12f);

        Table heading = new Table();
        if (unread) {
            heading.add(new Image(context.assets().round(MenuStyles.BADGE_RED)))
                    .size(DOT_SIZE).padRight(8f);
        } else {
            heading.add().size(DOT_SIZE).padRight(8f);
        }
        Label title = MenuStyles.label(skin, story.getTitle(), MenuStyles.HEADING);
        title.setAlignment(Align.left);
        title.setColor(unread ? UNREAD_TITLE : Color.WHITE);
        heading.add(title).growX().left();
        heading.add(tag(story.getType())).right();
        row.add(heading).growX().row();

        Label body = MenuStyles.label(skin, story.getDescription(), MenuStyles.TEXT);
        body.setWrap(true);
        body.setAlignment(Align.left);
        row.add(body).growX().padTop(4f).row();

        Label meta = MenuStyles.label(skin, story.getAuthor() + "  -  " + story.getDate(),
                MenuStyles.TEXT);
        meta.setAlignment(Align.left);
        meta.setColor(META);
        meta.setFontScale(0.75f);
        row.add(meta).growX().padTop(4f).row();
        return row;
    }

    // The story's category, as a short word rather than the enum constant. NETWORK and PLAYER_UPDATE
    // are named here too even though nothing files them yet -- they are the reason NewsType exists, and
    // a screen that renders "PLAYER_UPDATE" the day one arrives is a worse welcome than one that does
    // not.
    private Label tag(NewsType type) {
        String text = switch (type) {
            case PLANT_UNLOCK -> "New Plant";
            case ZOMBIE_DISCOVERY -> "New Zombie";
            case MINIGAME_UNLOCK -> "Mini-game";
            case LEVEL_UNLOCK -> "New Level";
            case NETWORK -> "Bulletin";
            case PLAYER_UPDATE -> "Player";
            case SYSTEM -> "Notice";
        };
        Label label = MenuStyles.label(skin, text, MenuStyles.TEXT);
        label.setColor(META);
        label.setFontScale(0.75f);
        return label;
    }

    private int unreadCount() {
        return NewsSystem.getInstance().getUnreadNews(profile()).size();
    }

    private String summary() {
        Profile profile = profile();
        int total = profile == null || profile.getNewsList() == null ? 0 : profile.getNewsList().size();
        int unread = unreadCount();
        if (total == 0) {
            return "The presses are quiet.";
        }
        return unread == 0
                ? total + (total == 1 ? " story" : " stories") + " in the archive, all read."
                : unread + " unread of " + total + ".";
    }

    private Profile profile() {
        User user = context.appSession().getCurrentUser();
        return user == null ? null : user.getProfile();
    }
}
