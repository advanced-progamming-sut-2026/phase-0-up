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
                .importPackages("models", "controllers", "views", "utils", "factories");
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
