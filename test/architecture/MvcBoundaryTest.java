package architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

// The MVC boundary, enforced by the build instead of by discipline.
//
// Phase 2 replaces the terminal View with a LibGDX one, and the entire plan depends on the models
// staying free of both. If a Texture or a SpriteBatch ever reaches models/, the GUI stops being a
// swappable layer: the terminal build breaks, the models stop being unit-testable without a GL
// context, and the phase-2 requirement to change the View "with minimal changes to the Models" is
// quietly no longer true. That is easy to violate by accident (one convenient Vector2 import) and
// almost impossible to notice in review, so it is checked here.
//
// If this test fails, do not relax the rule -- move the offending code into views/ instead.
class MvcBoundaryTest {

    private static JavaClasses productionClasses;

    @BeforeAll
    static void importClasses() {
        productionClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("models", "controllers", "views", "utils", "factories", "net",
                        "server");
    }

    // Guards the guards. Every rule below is phrased as "no class may ...", which passes trivially if
    // the importer found nothing -- a renamed source folder or a changed package layout would turn this
    // whole file into a green no-op without anyone noticing. Assert there is actually something to check.
    @Test
    @DisplayName("the importer actually scanned the production code")
    void importedClassesAreNotEmpty() {
        int count = productionClasses.size();
        org.junit.jupiter.api.Assertions.assertTrue(count > 300,
                "expected the whole game (~397 source files) to be imported, but only found " + count
                        + " classes -- the rules below would pass vacuously");
    }

    @Test
    @DisplayName("models must not depend on LibGDX")
    void modelsAreFreeOfLibGdx() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("models..")
                .should().dependOnClassesThat().resideInAnyPackage("com.badlogic.gdx..")
                .because("the models must stay renderable by any view, and unit-testable without a GL "
                        + "context -- put drawing code in views.gdx instead");
        rule.check(productionClasses);
    }

    @Test
    @DisplayName("models must not depend on the view layer")
    void modelsAreFreeOfViews() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("models..")
                .should().dependOnClassesThat().resideInAnyPackage("views..")
                .because("the model publishes (GameSession.reportEvent, Profile.setCurrencyObserver) "
                        + "and the view subscribes -- never the other way round");
        rule.check(productionClasses);
    }

    @Test
    @DisplayName("controllers must not depend on LibGDX")
    void controllersAreFreeOfLibGdx() {
        // Commands and systems are shared verbatim by both front ends. The moment one of them touches
        // Gdx.*, `gradlew run` stops working outside a window -- which is exactly what makes the
        // terminal build usable as a regression harness.
        ArchRule rule = noClasses()
                .that().resideInAPackage("controllers..")
                .should().dependOnClassesThat().resideInAnyPackage("com.badlogic.gdx..")
                .because("the 57 Commands are shared by the terminal and graphical builds and must run "
                        + "headless");
        rule.check(productionClasses);
    }

    @Test
    @DisplayName("controllers depend on renderer interfaces, never on an implementation")
    void controllersDependOnRendererInterfacesOnly() {
        // The Phase 3 invariant, and the one that quietly rots first. Every Command and both engines
        // are written against views.renderers..; the moment one of them says `new ConsoleAllMenuRenderer()`
        // or reaches for a Gdx renderer, that Command has picked a front end and the other build loses
        // it. StartLevelCommand did exactly this before the extraction, which is why it is a rule now
        // and not a convention.
        ArchRule rule = noClasses()
                .that().resideInAPackage("controllers..")
                .should().dependOnClassesThat().resideInAnyPackage("views.console..", "views.gdx..")
                .because("a Command must not know which View it is talking to -- only the composition "
                        + "root (Main / PvZGame) may name an implementation");
        rule.check(productionClasses);
    }

    @Test
    @DisplayName("the terminal View stays free of LibGDX")
    void consoleRenderersAreFreeOfLibGdx() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("views.console..")
                .should().dependOnClassesThat().resideInAnyPackage("com.badlogic.gdx..")
                .because("`gradlew run` must keep working with no window and no GL context");
        rule.check(productionClasses);
    }

    @Test
    @DisplayName("the graphical build never reads stdin")
    void graphicalBuildDoesNotBlockOnInput() {
        // views.InputHandler.readLine() blocks until a line arrives. LibGDX draws every frame on one
        // thread and runs click listeners on that same thread, so a single call from anywhere in the
        // GUI freezes the window with no error and no way out. RegisterCommand and ForgetPasswordCommand
        // still prompt -- that is what the terminal needs -- but they now have non-interactive
        // constructors, and this is what keeps the graphical path on them.
        ArchRule rule = noClasses()
                .that().resideInAPackage("views.gdx..")
                .should().dependOnClassesThat().haveFullyQualifiedName("views.InputHandler")
                .because("a blocking read on the render thread deadlocks the window; use the "
                        + "non-interactive Command constructors instead");
        rule.check(productionClasses);
    }

    @Test
    @DisplayName("the packet layer stays a pure data vocabulary")
    void netIsFreeOfViewsAndGdx() {
        // net/ is the one package BOTH the client and the standalone server hold, so whatever it
        // depends on, both are forced to drag in. Keeping it to plain data is what lets the server run
        // with no window and no GL context at all.
        //
        // views is included in the ban list alongside LibGDX on purpose: a packet that reached for a
        // Toast or a renderer would compile perfectly well here and then fail on the server, where no
        // such thing exists -- and it would fail at RUNTIME, mid-match, rather than in this build.
        ArchRule rule = noClasses()
                .that().resideInAPackage("net..")
                .should().dependOnClassesThat().resideInAnyPackage("com.badlogic.gdx..", "views..",
                        "server..", "controllers..")
                .because("a packet is plain data: it is held by the client AND the headless server, "
                        + "so it may name models/records and nothing else");
        rule.check(productionClasses);
    }

    @Test
    @DisplayName("the server runs headless")
    void serverIsFreeOfLibGdxAndTheFrontEnds() {
        // The whole Phase 3 architecture rests on this one rule. The server runs the REAL GameEngine
        // -- same systems, same order, same win/loss evaluation as both front ends -- and it can only
        // do that because the simulation has no window in it. The moment anything under server/ names
        // a Texture, a Screen or a console renderer, `gradlew runServer` needs a display, and the
        // authoritative-simulation design is over.
        //
        // views.renderers is NOT banned, deliberately: the server implements views.Renderers to relay
        // the model's narration to both players. Those are interfaces the Commands are already written
        // against, which is exactly the seam being reused -- what is banned is the two concrete front
        // ends behind them.
        ArchRule rule = noClasses()
                .that().resideInAPackage("server..")
                .should().dependOnClassesThat().resideInAnyPackage("com.badlogic.gdx..",
                        "views.gdx..", "views.console..", "pvz.libpvz..")
                .because("the server has no display: it may use the renderer INTERFACES, never a "
                        + "front end that draws");
        rule.check(productionClasses);
    }

    @Test
    @DisplayName("the server never reads stdin")
    void serverDoesNotBlockOnInput() {
        // Same failure as the graphical build's, with a different shape. `gradlew runServer` sets no
        // standardInput, so views.InputHandler.readLine() would see EOF immediately and the server
        // would exit the instant it finished starting up -- looking exactly like a crash on launch.
        ArchRule rule = noClasses()
                .that().resideInAPackage("server..")
                .should().dependOnClassesThat().haveFullyQualifiedName("views.InputHandler")
                .because("the server is not a REPL; it parks on a latch and is stopped by a signal");
        rule.check(productionClasses);
    }

    @Test
    @DisplayName("the client front end never reaches into the server")
    void viewsDoNotDependOnTheServer() {
        // The client talks to the server through packets and nothing else. A direct call would compile
        // and work perfectly in a single-JVM test, and then not exist at all in the shipped client --
        // which is the worst way to find out, because the local run would look fine.
        ArchRule rule = noClasses()
                .that().resideInAPackage("views..")
                .should().dependOnClassesThat().resideInAPackage("server..")
                .because("client and server share the net/ vocabulary, never each other's code");
        rule.check(productionClasses);
    }

    @Test
    @DisplayName("the model layer never learns about the network")
    void modelsAreFreeOfTheNetwork() {
        // Same direction of dependency the view already obeys. The model publishes and something else
        // subscribes -- if a Plant or a GameSession could name a packet, the simulation would stop
        // being runnable offline, and `gradlew run` is the regression harness for all of it.
        ArchRule rule = noClasses()
                .that().resideInAPackage("models..")
                .should().dependOnClassesThat().resideInAnyPackage("net..", "server..", "java.net..")
                .because("the model must stay playable with no server attached -- the network reads "
                        + "the model, never the other way round");
        rule.check(productionClasses);
    }

    @Test
    @DisplayName("libPVZ is reachable only through the sprite abstraction")
    void libPvzIsConfinedToTheSpriteLayer() {
        // The PAM runtime is a third-party dependency with no fallback of its own. Confining it to
        // views.gdx.sprite (and the Assets loader that constructs it) is what lets a broken or missing
        // animation degrade to a still image instead of taking a renderer down.
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackages("views.gdx.sprite..", "views.gdx.core..")
                .should().dependOnClassesThat().resideInAnyPackage("pvz.libpvz..")
                .because("renderers must go through views.gdx.sprite.EntitySprite, which falls back to "
                        + "a still image when an animation is unavailable");
        rule.check(productionClasses);
    }
}
