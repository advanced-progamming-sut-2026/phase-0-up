package views.gdx.screens;

import com.badlogic.gdx.Gdx;
import controllers.engine.GameEngine;
import models.game.Chapter;
import models.game.GameSession;
import models.game.Level;
import models.game.SeedPacket;
import models.templates.PlantTemplate;
import models.user.AppSession;
import models.user.Profile;
import models.user.User;
import utils.Constants;
import utils.gameinitializers.LevelInitializer;
import utils.registry.PlantRegistry;

import java.util.List;

// Phase 1 scaffolding: drops straight into a real level so the board can be built before any menus
// exist. Deleted at T4.8, when Adventure -> Seed Selection -> Game becomes the real route in.
//
// It deliberately goes through the same objects the Commands do -- LevelInitializer, GameSession,
// GameEngine.init() -- rather than hand-assembling a board. Anything the real flow does that this
// skips would otherwise show up as a rendering bug much later.
public final class DevBoot {

    // Which level to open. Chapter/level are 1-based, matching the CLI.
    private static final String CHAPTER_PROPERTY = "pvz.devChapter";
    private static final String LEVEL_PROPERTY = "pvz.devLevel";

    // A serviceable opening loadout. Enough to actually shoot back, so the board has something moving
    // on it while the renderers are being written.
    private static final List<String> DEFAULT_SEEDS =
            List.of("Sunflower", "Peashooter", "Wall-nut", "Snow Pea", "Cherry Bomb", "Potato Mine");

    private final GameSession session;
    private final GameEngine engine;

    private DevBoot(GameSession session, GameEngine engine) {
        this.session = session;
        this.engine = engine;
    }

    public static DevBoot start(AppSession appSession, views.Renderers renderers) {
        Profile profile = resolveProfile(appSession);
        Level level = resolveLevel(profile);

        GameSession session = new GameSession(profile, level);
        appSession.setCurrentGameSession(session);

        selectSeeds(session);

        GameEngine engine = new GameEngine(session, renderers);
        engine.init();   // startMode(), seed boosts, quest tracking -- exactly what startLoop() does

        prePlant(session);

        Gdx.app.log("DevBoot", "level: "
                + (level.getTemplate() != null ? level.getTemplate().getName() : "(generated)")
                + " | mode: " + session.getMode().getClass().getSimpleName()
                + " | seeds: " + session.getSelectedSeeds().size()
                + " | sun: " + session.getSunAmount());
        return new DevBoot(session, engine);
    }

    // Uses the logged-in profile when there is one, otherwise the first saved user, otherwise a fresh
    // one. A fresh Profile still has to be told about its starter plants: the constructor grants them,
    // but a Gson-loaded one skips the constructor entirely, so the real login path calls this too.
    private static Profile resolveProfile(AppSession appSession) {
        User user = appSession.getCurrentUser();
        if (user == null) {
            user = utils.storage.DatabaseManager.getInstance().getLoggedInUser();
        }
        Profile profile = user != null ? user.getProfile() : new Profile();
        profile.ensureStartingPlants();
        LevelInitializer.attachCampaign(profile);
        return profile;
    }

    // Which mini-game to open instead of a campaign level, by the same names the Travel Log's tiles
    // use. Mini-games are built by MinigameFactory and belong to no chapter, so there is no
    // chapter/level pair that reaches one -- and the Travel Log route needs a click a screenshot run
    // cannot make.
    //
    //   gradlew runGui -Dpvz.screen=game -Dpvz.devMinigame=vasebreaker -Dpvz.skipIntro=1
    private static final String MINIGAME_PROPERTY = "pvz.devMinigame";
    private static final String DIFFICULTY_PROPERTY = "pvz.devDifficulty";

    private static Level resolveLevel(Profile profile) {
        Level minigame = resolveMinigame();
        if (minigame != null) {
            return minigame;
        }
        int chapterNumber = intProperty(CHAPTER_PROPERTY, 1);
        int levelNumber = intProperty(LEVEL_PROPERTY, 1);

        // buildChapters() rather than profile.getUnlockedChapters(): the dev boot must be able to open
        // any level for rendering work, including ones this profile has not reached.
        List<Chapter> chapters = LevelInitializer.buildChapters();
        Chapter chapter = chapters.get(clamp(chapterNumber, 1, chapters.size()) - 1);
        Level[] levels = chapter.getLevels();
        return levels[clamp(levelNumber, 1, levels.length) - 1];
    }

    // Null when the property is absent, which is the ordinary campaign path.
    private static Level resolveMinigame() {
        String wanted = System.getProperty(MINIGAME_PROPERTY, "").trim().toLowerCase(java.util.Locale.ROOT);
        if (wanted.isEmpty()) {
            return null;
        }
        int difficulty = intProperty(DIFFICULTY_PROPERTY, 1);
        Level level = switch (wanted) {
            case "vasebreaker" -> factories.MinigameFactory.createVasebreaker(difficulty);
            case "bowling", "wallnutbowling" -> factories.MinigameFactory.createWallnutBowling(difficulty);
            case "izombie" -> factories.MinigameFactory.createIZombie(difficulty);
            case "beghouled" -> factories.MinigameFactory.createBeghouled(difficulty);
            case "zombotany" -> factories.MinigameFactory.createZombotany(difficulty);
            // Not a mini-game strictly -- the scoring game is reachable from the Adventure screen, which
            // a screenshot run cannot click. It belongs here for the same reason the others do: it is a
            // board with no chapter/level pair that reaches it.
            case "scoring" -> factories.MinigameFactory.createScoringGame();
            default -> null;
        };
        if (level == null) {
            Gdx.app.error("DevBoot", "-D" + MINIGAME_PROPERTY + "=" + wanted + " is not a mini-game");
        }
        return level;
    }

    // Mirrors ToggleSeedCommand's construction (recharge seconds rounded to a tick cooldown) without
    // its menu-state validation, which does not apply before there is a menu.
    private static void selectSeeds(GameSession session) {
        if (session.getMode().managesPlantInventory()) {
            return;   // conveyor / Vasebreaker style levels hand out their own plants
        }
        List<String> available = session.getLevel().getAvailablePlants();
        int slots = session.getMaxSeedSlots();

        for (String name : DEFAULT_SEEDS) {
            if (session.getSelectedSeeds().size() >= slots) {
                break;
            }
            PlantTemplate template = PlantRegistry.getInstance().getTemplateByName(name);
            if (template == null) {
                continue;
            }
            // Respect the level's own plant pool when it defines one, so a level that bans a plant
            // still bans it here.
            if (available != null && !available.isEmpty()
                    && available.stream().noneMatch(p -> p.equalsIgnoreCase(name))) {
                continue;
            }
            session.addSeed(new SeedPacket(template.getName(),
                    (int) Math.round(template.getRecharge())));
        }
    }

    // Puts a few plants on the board immediately so the renderers have something to draw before the
    // first wave arrives. Goes through GameSession.plant -- the same entry point PlantSeedCommand
    // uses -- so cell stacking, terrain rules and cooldowns all behave exactly as in a real game.
    //
    // Disable with -Dpvz.devPlants=0 to see a genuinely empty lawn.
    private static void prePlant(GameSession session) {
        if ("0".equals(System.getProperty("pvz.devPlants", "1"))) {
            return;
        }
        if (session.getMode().managesPlantInventory()) {
            return;
        }
        // Plenty of sun, so placement is never refused for affordability while testing.
        session.increaseSunAmount(2000);

        for (int row = 0; row < utils.Constants.BOARD_ROWS; row++) {
            place(session, 0, row, "Sunflower");
            place(session, 1, row, "Peashooter");
        }
        place(session, 4, 2, "Wall-nut");
        place(session, 4, 1, "Snow Pea");
    }

    private static void place(GameSession session, int col, int row, String plantType) {
        if (!session.isSeedSelected(plantType)) {
            return;
        }
        utils.Result result = session.plant(col, row, plantType);
        if (!result.success()) {
            Gdx.app.log("DevBoot", "could not place " + plantType + " at (" + col + ", " + row + "): "
                    + result.message());
        }
    }

    private static int intProperty(String key, int fallback) {
        String raw = System.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public GameSession session() {
        return session;
    }

    public GameEngine engine() {
        return engine;
    }

    // Convenience for the HUD-less Phase 1 screens.
    public int maxSeedSlots() {
        return Math.min(session.getMaxSeedSlots(), Constants.DEFAULT_SEED_SLOTS);
    }
}
