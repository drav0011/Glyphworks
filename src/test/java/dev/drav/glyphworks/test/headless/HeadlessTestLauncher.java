package dev.drav.glyphworks.test.headless;

import java.util.List;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import dev.drav.glyphworks.test.framework.TestRegistry;
import dev.drav.glyphworks.test.framework.TestRunEntry;
import dev.drav.glyphworks.test.runner.TestRunLauncher;
import dev.drav.glyphworks.test.world.TestWorldManager;

/**
 * Starts a test run without a player — triggered by JVM system properties when
 * the server is launched via {@code ./gradlew runServer}.
 *
 * <h3>Supported system properties</h3>
 * 
 * <pre>
 *   -Dglyphworks.test.module=&lt;name&gt;   Module to run (omit = all modules)
 *   -Dglyphworks.test.suite=&lt;name&gt;    Suite within the module (omit = all)
 *   -Dglyphworks.test.name=&lt;name&gt;     Single test within the suite (omit = all)
 *   -Dglyphworks.test.no-cleanup      Keep the test world after the run
 * </pre>
 *
 * <p>
 * If any property is present, {@link dev.drav.glyphworks.test.GlyphworksTestPlugin} will wait for all
 * worlds to finish loading and then call {@link #launch()}. The server exits
 * automatically once
 * the run completes (exit 0 = all passed, exit 1 = any failure).
 */
public final class HeadlessTestLauncher {

    private static final Logger LOGGER = Logger.getLogger(HeadlessTestLauncher.class.getName());

    /**
     * System property key that enables headless mode (module to run; null = all).
     */
    public static final String PROP_MODULE = "glyphworks.test.module";
    /** System property key for the suite to run within the module. */
    public static final String PROP_SUITE = "glyphworks.test.suite";
    /** System property key for the specific test to run within the suite. */
    public static final String PROP_TEST_NAME = "glyphworks.test.name";

    private HeadlessTestLauncher() {
    }

    /**
     * Returns {@code true} if at least one headless trigger property is set,
     * meaning the server should run tests on startup.
     */
    public static boolean isEnabled() {
        return System.getProperty(PROP_MODULE) != null
                || System.getProperty(PROP_SUITE) != null
                || System.getProperty(PROP_TEST_NAME) != null
                || System.getProperty("glyphworks.test.all") != null;
    }

    /**
     * Builds the test queue from system properties, creates an isolated test world,
     * spawns a headless runner entity inside it, and returns.
     *
     * <p>
     * The {@link dev.drav.glyphworks.test.runner.TestRunnerSystem} will tick the
     * runner and
     * shut the server down when done.
     */
    public static void launch() {
        @Nullable
        String moduleName = System.getProperty(PROP_MODULE);
        @Nullable
        String suiteName = System.getProperty(PROP_SUITE);
        @Nullable
        String testName = System.getProperty(PROP_TEST_NAME);
        boolean cleanupAfterRun = true;

        List<TestRunEntry> queue = TestRegistry.buildQueue(moduleName, suiteName, testName,
                msg -> LOGGER.severe("[GlyphTest] Headless: " + msg));
        if (queue == null) {
            System.exit(1);
            return;
        }
        if (queue.isEmpty()) {
            LOGGER.warning("[GlyphTest] Headless: no tests found for the given target \u2014 exiting.");
            System.exit(1);
            return;
        }

        LOGGER.info("[GlyphTest] Headless: starting " + queue.size() + " test(s)");
        String runId = TestWorldManager.newRunId();
        LOGGER.info("[GlyphTest] Headless: test world will be glyphworks-test-" + runId);
        TestRunLauncher.launch(queue, cleanupAfterRun, null, true, runId, msg -> {
            LOGGER.severe("[GlyphTest] Headless: " + msg);
            System.exit(1);
        });
    }
}
