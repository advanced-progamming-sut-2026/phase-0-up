package views.gdx.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import controllers.engine.MenuType;
import controllers.systems.NewsSystem;
import models.user.Profile;
import models.user.User;
import views.gdx.core.GdxContext;
import views.gdx.ui.MenuStyles;

// The main menu.
//
// Six buttons, each posting the command the terminal's main menu already accepts -- the screen decides
// nothing about the game, only about how it feels to press. The News button carries the unread badge
// the spec asks for, recomputed every frame rather than at build time, because reading news elsewhere
// has to clear it and a count captured once would go stale the moment the player came back.
//
// Everything animated here is layout-safe, which drives most of the odd-looking choices below. Scene2D
// rewrites an actor's position and size on every layout pass, so anything animating those has to sit
// inside a plain Group; scale, rotation and colour are never touched by layout and can go anywhere.
public final class MainMenuScreen extends MenuScreen {

    // The shipped logo, drawn at a size that clears the panel beneath it. Its source is 402x68 at this
    // resolution, so this is a mild upscale and the proportion is the art's own.
    private static final String LOGO = "IMAGE_UI_MAINMENU_PVZ2_LOGO_HORIZONTAL";
    private static final float LOGO_WIDTH = 470f;
    private static final float LOGO_HEIGHT = LOGO_WIDTH * 68f / 402f;

    private Label badge;
    private Container<Label> badgeHolder;
    private Label greeting;
    private Table content;

    public MainMenuScreen(GdxContext context) {
        super(context);
    }

    @Override
    protected void build(Table root) {
        Table panel = MenuPanel.build(skin, null);

        greeting = MenuStyles.label(skin, greetingText(), MenuStyles.HEADING);
        panel.add(greeting).padBottom(14f).row();

        playButton = menuButton("Play", MenuStyles.BUTTON_GREEN,
                () -> commands.enter(MenuType.PLAY_MENU.getMenuName()));
        panel.add(playButton).width(300f).height(66f).padBottom(8f).row();
        panel.add(menuButton("Profile", MenuStyles.BUTTON_BROWN,
                () -> commands.enter(MenuType.PROFILE_MENU.getMenuName()))).width(300f).height(60f)
                .padBottom(8f).row();
        // The way into the lobby. The menu graph already allowed MAIN_MENU -> ONLINE_MENU and
        // ExitMenuCommand already routed back out -- the edge was there from an earlier phase with
        // nothing on the other end of it.
        panel.add(menuButton("Multiplayer", MenuStyles.BUTTON_BROWN,
                () -> commands.enter(MenuType.ONLINE_MENU.getMenuName()))).width(300f).height(60f)
                .padBottom(8f).row();
        panel.add(menuButton("Settings", MenuStyles.BUTTON_BROWN,
                () -> commands.enter(MenuType.SETTINGS_MENU.getMenuName()))).width(300f).height(60f)
                .padBottom(8f).row();
        panel.add(newsButton()).width(300f).height(60f).padBottom(8f).row();
        panel.add(menuButton("Sign Out", MenuStyles.BUTTON_PURPLE,
                () -> commands.submit("menu logout"))).width(300f).height(60f).padBottom(8f).row();
        panel.add(menuButton("Quit", MenuStyles.BUTTON_BROWN, this::quit))
                .width(300f).height(52f).row();

        content = new Table();
        content.add(logo()).size(LOGO_WIDTH, LOGO_HEIGHT).padBottom(2f).row();
        content.add(panel).row();

        root.setFillParent(true);
        root.add(content);
    }

    // The logo, inside a Group of its own.
    //
    // The Group is what the Table sizes and positions; the logo floats freely inside it. Put straight
    // in a cell, the float would be undone the first time anything invalidated the layout, because a
    // Table rewrites its children's coordinates every pass -- and the logo would settle wherever the
    // animation happened to be when that occurred.
    private Group logo() {
        Group holder = new Group();
        holder.setSize(LOGO_WIDTH, LOGO_HEIGHT);

        TextureRegion region = region(LOGO);
        Actor art;
        if (region != null) {
            Image image = new Image(new TextureRegionDrawable(region));
            image.setSize(LOGO_WIDTH, LOGO_HEIGHT);
            art = image;
        } else {
            // The art is the title. If it ever fails to resolve, the screen still needs one.
            Label fallback = MenuStyles.title(skin, "Plants vs. Zombies 2");
            fallback.setSize(LOGO_WIDTH, LOGO_HEIGHT);
            fallback.setAlignment(Align.center);
            art = fallback;
        }

        art.addAction(Actions.forever(Actions.sequence(
                Actions.moveBy(0f, 10f, 2f, Interpolation.sine),
                Actions.moveBy(0f, -10f, 2f, Interpolation.sine))));
        holder.addActor(art);
        return holder;
    }

    // The News button with a red count pinned to its top-right corner, the way the terminal build
    // marks the same thing with an ANSI badge.
    private Stack newsButton() {
        TextButton button = menuButton("News", MenuStyles.BUTTON_BROWN,
                () -> commands.enter(MenuType.NEWS_MENU.getMenuName()));

        badge = new Label("", skin);
        badge.setAlignment(Align.center);
        badgeHolder = new Container<>(badge);
        badgeHolder.setBackground(context.assets().round(MenuStyles.BADGE_RED));
        badgeHolder.size(30f).align(Align.topRight).pad(4f);

        Table overlay = new Table();
        overlay.add(badgeHolder).expand().top().right();
        // The badge is decoration on top of the button; without this it would swallow the clicks that
        // land on the corner it covers.
        overlay.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);

        Stack stack = new Stack();
        stack.add(button);
        stack.add(overlay);
        return stack;
    }

    // Hover and press feedback comes from MenuStyles.button, which every screen uses -- see ButtonJuice.
    // All this adds is what the button does.
    private TextButton menuButton(String text, String style, Runnable action) {
        TextButton button = MenuStyles.button(skin, text, style);
        button.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                action.run();
            }
        });
        return button;
    }

    // Proves the hover actually fires, since a screenshot run has no mouse to move.
    //
    // -Dpvz.hoverCheck=1 parks the pointer on the Play button and reports its scale once the tween has
    // settled. Worth having as a flag rather than a one-off: "the listener is attached" and "the
    // listener changes anything" are different claims, and only the second one matters.
    private void reportHoverCheck() {
        if (!views.gdx.core.DebugFlags.HOVER_CHECK || playButton == null) {
            return;
        }
        com.badlogic.gdx.math.Vector2 centre = playButton.localToStageCoordinates(
                new com.badlogic.gdx.math.Vector2(playButton.getWidth() / 2f,
                        playButton.getHeight() / 2f));
        stage.getViewport().project(centre);
        // y is flipped: project() returns y-up (OpenGL), mouseMoved expects y-down (the mouse).
        stage.mouseMoved((int) centre.x, (int) (Gdx.graphics.getHeight() - centre.y));
        // Let the 0.12s tween finish before reading the result.
        for (int i = 0; i < 12; i++) {
            stage.act(1f / 60f);
        }
        Gdx.app.log("HoverCheck", "Play button scale=" + playButton.getScaleX()
                + " colour=" + playButton.getColor().r
                + " (rest is 1.0 / " + views.gdx.ui.ButtonJuice.REST.r + ")");
    }

    private TextButton playButton;

    // Saves through the same "exit application" the terminal runs, then closes the window. Without the
    // second half the command would tidy up and the window would sit there.
    private void quit() {
        commands.submit("exit application");
        Gdx.app.exit();
    }

    private boolean hoverChecked;

    @Override
    protected void refresh() {
        // After the entrance has settled, so the check reads the hover's scale and not the entrance's.
        if (!hoverChecked && content != null && root.getActions().size == 0) {
            hoverChecked = true;
            reportHoverCheck();
        }
        int unread = unreadCount();
        badgeHolder.setVisible(unread > 0);
        badge.setText(unread > 9 ? "9+" : String.valueOf(unread));
        greeting.setText(greetingText());
    }

    private int unreadCount() {
        Profile profile = profile();
        return profile == null ? 0 : NewsSystem.getInstance().getUnreadNews(profile).size();
    }

    private String greetingText() {
        User user = context.appSession().getCurrentUser();
        return user == null ? "" : "Welcome back, " + user.getNickname() + "!";
    }

    private Profile profile() {
        User user = context.appSession().getCurrentUser();
        return user == null ? null : user.getProfile();
    }

    // Nowhere above the main menu -- Escape here should not sign the player out.
    @Override
    protected void goBack() {
    }
}
