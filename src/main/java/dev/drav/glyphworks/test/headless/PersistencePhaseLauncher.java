package dev.drav.glyphworks.test.headless;

import java.util.List;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import dev.drav.glyphworks.test.framework.TestCase;
import dev.drav.glyphworks.test.framework.TestRegistry;
import dev.drav.glyphworks.test.runner.TestRunLauncher;

/**
 * Starts a two-phase persistence test run without a player — triggered by JVM
 * system properties when the server is launched via {@code ./gradlew runServer}.
 *
 * <p>
 * Run the setup phase first, then the assert phase on the next server start:
 *
 * <pre>
 *   # Phase 1 — setup
 *   $env:JAVA_TOOL_OPTIONS="-Dglyphworks.test.persistence.phase=setup -Dglyphworks.test.persistence.module=fluid"
 *   ./gradlew runServer
 *
 *   # Phase 2 — assert
 *   $env:JAVA_TOOL_OPTIONS="-Dglyphworks.test.persistence.phase=assert -Dglyphworks.test.persistence.module=fluid"
 *   ./gradlew runServer
 * </pre>
 *
 * <h3>Supported system properties</h3>
 * <pre>
 *   -Dglyphworks.test.persistence.phase=setup|assert   Required
 *   -Dglyphworks.test.persistence.module=&lt;name&gt;       Module to run (omit = all)
 *   -Dglyphworks.test.persistence.suite=&lt;name&gt;        Suite within module (omit = all)
 *   -Dglyphworks.test.persistence.name=&lt;name&gt;         Single test within suite
 *   -Dglyphworks.test.persistence.all=true             Run all registered modules
 *   -Dglyphworks.test.persistence.no-cleanup           Keep persistence world after assert
 * </pre>
 */
public final class PersistencePhaseLauncher {

    private static final Logger LOGGER = Logger.getLogger(PersistencePhaseLauncher.class.getName());

    static final String PROP_PHASE   = "glyphworks.test.persistence.phase";
    static final String PROP_MODULE  = "glyphworks.test.persistence.module";
    static final String PROP_SUITE   = "glyphworks.test.persistence.suite";
    static final String PROP_NAME    = "glyphworks.test.persistence.name";
    static final String PROP_ALL     = "glyphworks.test.persistence.all";
    static final String PROP_NO_CLEANUP = "glyphworks.test.persistence.no-cleanup";

    private PersistencePhaseLauncher() {
    }

    /**
     * Returns {@code true} if the persistence phase property is set, meaning the
     * server should run a persistence test phase on startup.
     */
    public static boolean isEnabled() {
        return System.getProperty(PROP_PHASE) != null;
    }

    /**
     * Reads the phase and target properties, builds the test queue, and dispatches
     * to the appropriate launcher method.
     */
    public static void launch() {
        String phase = System.getProperty(PROP_PHASE);
        if (phase == null || (!phase.equals("setup") && !phase.equals("assert"))) {
            LOGGER.severe("[GlyphTest] Persistence: invalid phase \"" + phase
                    + "\" — expected \"setup\" or \"assert\".");
            System.exit(1);
            return;
        }

        @Nullable String moduleName = System.getProperty(PROP_MODULE);
        @Nullable String suiteName  = System.getProperty(PROP_SUITE);
        @Nullable String testName   = System.getProperty(PROP_NAME);

        List<TestCase> queue = TestRegistry.buildQueue(moduleName, suiteName, testName,
                msg -> LOGGER.severe("[GlyphTest] Persistence: " + msg));
        if (queue == null) {
            System.exit(1);
            return;
        }
        if (queue.isEmpty()) {
            LOGGER.warning("[GlyphTest] Persistence: no tests found for the given target — exiting.");
            System.exit(1);
            return;
        }

        LOGGER.info("[GlyphTest] Persistence (" + phase + "): starting " + queue.size() + " test(s)");

        if (phase.equals("setup")) {
            runSetup(queue);
        } else {
            runAssert(queue);
        }
    }

    private static void runSetup(List<TestCase> queue) {
        TestRunLauncher.launchPersistenceSetup(queue, null, true, msg -> {
            LOGGER.severe("[GlyphTest] Persistence setup: " + msg);
            System.exit(1);
        });
    }

    private static void runAssert(List<TestCase> queue) {
        boolean cleanupAfterRun = System.getProperty(PROP_NO_CLEANUP) == null;
        TestRunLauncher.launchPersistenceAssert(queue, cleanupAfterRun, null, true, msg -> {
            LOGGER.severe("[GlyphTest] Persistence assert: " + msg);
            System.exit(1);
        });
    }
}
