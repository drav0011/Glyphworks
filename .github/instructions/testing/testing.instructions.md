---
description: "Agent coding instructions — In-Game Testing Framework. Apply these rules when writing or reviewing Glyphworks tests."
applyTo: "**/*.java"
---

# In-Game Testing Framework

Apply these principles consistently when writing, reviewing, or suggesting code changes.

## Core Rules

### There is no JUnit — all tests run in-game
- Tests execute inside a live Hytale server world via the ECS `TestRunnerSystem`.
- Each test gets its own isolated bounding box in a dedicated test world.
- `TestRunnerSystem` ticks through test steps one per game tick.

### Structure tests as TestCase with sequential steps
- A `TestCase` declares a name, a bounding box size (`areaWidth`, `areaDepth`, `areaHeight`), and an ordered list of `TestStep`s.
- Steps execute sequentially — one per tick unless a `wait` extends it.
- If any step returns `StepResult.failed(reason)`, remaining steps are skipped and the test fails.
- Chain steps via the builder: `new TestCase("name", w, d, h).step(...).step(...).step(...)`.

### Use the Steps factory for all step types
- `Steps.run(ctx -> ...)` — execute an action synchronously, returns `DONE` immediately.
- `Steps.wait(ticks)` — pause for exactly N ticks.
- `Steps.wait(ctx -> ticks)` — pause for a computed number of ticks (e.g., `ctx -> 2 * ctx.getWorld().getTps()` for 2 seconds).
- `Steps.assertThat(ctx -> predicate, description)` — pass if predicate is true, fail with description otherwise.
- `Steps.waitUntil(predicate, maxTicks, description)` — poll predicate every tick; pass on true, fail after maxTicks.
- `Steps.waitUntilOrFail(success, fail, maxTicks, description)` — fail immediately if the fail predicate fires before success.

### Follow the setup → wait → assert pattern
- Step 1: `Steps.run(...)` to place blocks, inject state, or trigger actions.
- Step 2: `Steps.wait(...)` to give systems time to tick and propagate state.
- Step 3: `Steps.assertThat(...)` to verify the expected outcome.
- More complex tests may chain multiple setup-wait-assert cycles.

### Always use origin offsets for block positions
- `ctx.getOriginX()`, `ctx.getOriginY()`, `ctx.getOriginZ()` provide the test's allocated world position.
- Never use hardcoded absolute coordinates — tests are laid out in a grid and origins vary.

### Use TPS-relative waits, not fixed tick counts
- Prefer `Steps.wait(ctx -> N * ctx.getWorld().getTps())` over `Steps.wait(N)` for time-based waits.
- Fixed tick counts break silently if the TPS changes; TPS-relative waits adapt automatically.
- Use fixed tick counts only when the exact tick count matters (e.g., testing a 2-tick delay component).

### Group tests into TestSuites, register via TestRegistry
- A `TestSuite` is a named collection of `TestCase`s: `new TestSuite("suite_id").test(case1).test(case2)`.
- Register suites in the module's `setupTests()` method via `TestRegistry.register(moduleId, suite)`.
- Suite IDs use `snake_case`, test names use `snake_case`.

### One test class per domain concern
- Each test class is a static utility with a `register(moduleId)` method and a private `buildSuite()` method.
- Test methods are private static factories returning `TestCase`.
- Name the class after what it tests: `FluidSourceSystemTests`, `GridComponentTests`.

### Separate component tests and system tests into subdirectories
- System tests (integration tests that exercise ticking systems) live in `<module>/tests/system/`.
- Component persistence tests (verify codec round-trip across server restarts) live in `<module>/tests/component/`.
- Shared test utilities (`FluidTestUtil`, `ItemTestUtil`, etc.) stay at the `<module>/tests/` level and must be `public`.
- Java package names mirror the directory: `dev.drav.glyphworks.fluid.tests.system`, `dev.drav.glyphworks.fluid.tests.component`.

### Write a persistence test for every component with serialised fields
- Any `Component` whose `BuilderCodec` encodes at least one field needs a persistence test.
- Marker components with an empty codec (e.g. `FluidSinkComponent`, `ItemDropperComponent`) need no persistence test.
- The test class lives in `<module>/tests/component/` and is named `<ComponentName>Tests.java`.
- Each persistence test class registers two suites: a `_setup` suite and an `_assert` suite.
- Suite IDs follow the pattern `<module>_<component_snake>_persistence_setup` / `_assert`.

### Persistence test structure: setup + assert
- **Setup suite** — places the block, waits for block-entity initialisation, writes known non-default values to all serialised fields, asserts baseline (verifies state before the server stops).
- **Assert suite** — waitUntil the component exists again (block entity re-hydrated after engine restart), then asserts that every serialised field matches the values written in setup.
- Use `waitUntil` (not `wait`) in both phases: block-entity hydration is async after chunk load.
- Both suites use the same test-case bounding box so the test occupies the same world position in both phases.

### Persistence test suite naming and registration
- Register both suites in the same `register(moduleId)` call.
- The persistence world is a single fixed-name world shared across all component test suites; positions are allocated from the same test-area grid as regular tests.

### Keep test bounding boxes minimal
- Set `areaWidth`, `areaDepth`, `areaHeight` to the smallest size that fits the test scenario.
- Larger boxes slow down world setup and increase the grid layout footprint.
- A single-block test uses `(1, 1, 1)`. A multi-block pipe chain might use `(5, 3, 1)`.

### Access components through standard Hytale APIs
- Use `ctx.getWorld()` for block placement and world queries.
- Use `ctx.getWorld().getChunkStore()` or `ctx.getStore()` for component access.
- There are no special test helpers for reading components — use the same APIs as production code.

### Run tests headless after writing or modifying them
- Compile first, then launch with the appropriate JVM property:
  ```powershell
  .\gradlew compileJava
  $env:JAVA_TOOL_OPTIONS="-Dglyphworks.test.module=<module>" ; ./gradlew runServer ; Remove-Item Env:JAVA_TOOL_OPTIONS
  ```
- Exit `0` = all passed, exit `1` = any failure. Check `devserver/logs/` for output.
- Use `glyphworks.test.all=true` to run every registered module.

## Code Examples

### Complete test class structure

```java
public final class FluidSourceSystemTests {

    private static final String SOURCE_ID = "Glyphworks_Fluid_Source";

    public static void register(String moduleId) {
        TestRegistry.register(moduleId, buildSuite());
    }

    private static TestSuite buildSuite() {
        return new TestSuite("fluid_source_system")
                .test(sourceFillsContainer())
                .test(sourceOverridesStaleState());
    }

    private static TestCase sourceFillsContainer() {
        return new TestCase("source_fills_container", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    ctx.getWorld().setBlock(
                            ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ(),
                            SOURCE_ID);
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    FluidContainerComponent fcc = FluidTestUtil.getContainer(ctx.getWorld(),
                            new Vector3i(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()));
                    return fcc != null
                            && fcc.getAmount() == fcc.getCapacity();
                }, "source fills container to capacity"));
    }
}
```

### Registering tests in a module

```java
public class FluidModule extends GlyphworksModule {

    @Override
    public void setupTests() {
        // system/
        FluidSourceSystemTests.register("fluid");
        FluidGridTransferTests.register("fluid");
        FluidSinkSystemTests.register("fluid");
        // component/
        FluidContainerComponentTests.register("fluid");
        FluidPipeComponentTests.register("fluid");
    }
}
```

### Component persistence test class structure

```java
// In fluid/tests/component/FluidContainerComponentTests.java
// Package: dev.drav.glyphworks.fluid.tests.component

public final class FluidContainerComponentTests {

    private static final String TANK_ID  = "Glyphworks_Fluid_Tank";
    private static final String FLUID_ID = "Mana_Source";
    private static final int    AMOUNT   = 750;

    private FluidContainerComponentTests() {
    }

    public static void register(String moduleId) {
        TestRegistry.register(moduleId, buildSetupSuite());
        TestRegistry.register(moduleId, buildAssertSuite());
    }

    private static TestSuite buildSetupSuite() {
        return new TestSuite("fluid_container_persistence_setup")
                .test(setupContainerState());
    }

    private static TestSuite buildAssertSuite() {
        return new TestSuite("fluid_container_persistence_assert")
                .test(assertContainerState());
    }

    private static TestCase setupContainerState() {
        return new TestCase("fluid_container_persists_state", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    ctx.getWorld().setBlock(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ(), TANK_ID);
                }))
                .step(Steps.waitUntil(ctx -> {
                    FluidContainerComponent fcc = FluidTestUtil.getContainer(ctx.getWorld(),
                            new Vector3i(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()));
                    return fcc != null;
                }, ctx -> 5 * ctx.getWorld().getTps(), "tank block entity initialised"))
                .step(Steps.run(ctx -> {
                    FluidContainerComponent fcc = FluidTestUtil.getContainer(ctx.getWorld(),
                            new Vector3i(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()));
                    if (fcc != null) {
                        fcc.setAmount(AMOUNT);
                        fcc.setFluidId(FLUID_ID);
                    }
                }))
                .step(Steps.assertThat(ctx -> {
                    FluidContainerComponent fcc = FluidTestUtil.getContainer(ctx.getWorld(),
                            new Vector3i(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()));
                    return fcc != null && fcc.getAmount() == AMOUNT && FLUID_ID.equals(fcc.getFluidId());
                }, "baseline: container holds " + AMOUNT + "L of " + FLUID_ID + " before server stop"));
    }

    private static TestCase assertContainerState() {
        return new TestCase("fluid_container_persists_state", 3, 3, 3)
                .step(Steps.waitUntil(ctx -> {
                    FluidContainerComponent fcc = FluidTestUtil.getContainer(ctx.getWorld(),
                            new Vector3i(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()));
                    return fcc != null;
                }, ctx -> 5 * ctx.getWorld().getTps(), "container reloaded after restart"))
                .step(Steps.assertThat(ctx -> {
                    FluidContainerComponent fcc = FluidTestUtil.getContainer(ctx.getWorld(),
                            new Vector3i(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()));
                    return fcc != null && fcc.getAmount() == AMOUNT && FLUID_ID.equals(fcc.getFluidId());
                }, "FluidContainerComponent persists amount and fluidId across server restart"));
    }
}
```

### Directory and package layout

```
fluid/tests/
    FluidTestUtil.java              ← public; shared by both subdirs
    component/
        FluidContainerComponentTests.java
        FluidPipeComponentTests.java
        FluidSourceComponentTests.java
        ...
    system/
        FluidGridTransferTests.java
        FluidSourceSystemTests.java
        ...

item/tests/
    ItemTestUtil.java               ← public
    component/
        ItemSourceComponentTests.java
        BlockMinerComponentTests.java
        ...
    system/
        ItemGridTransferTests.java
        ItemSourceSystemTests.java
        ...

grid/tests/
    GridTestUtil.java               ← public
    component/
        GridComponentTests.java
        GridFaceUtilTests.java
    system/
        GridGraphTests.java
        GridBlockChangeTests.java
        GridConnectionTests.java
        PipeConnectionTests.java
```

### Using waitUntil for async system convergence

```java
private static TestCase pipeTransfersFluid() {
    return new TestCase("pipe_transfers_fluid", 5, 3, 1)
            .step(Steps.run(ctx -> {
                int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                ctx.getWorld().setBlock(x, y, z, SOURCE_ID);
                ctx.getWorld().setBlock(x + 1, y, z, PIPE_ID);
                ctx.getWorld().setBlock(x + 2, y, z, TANK_ID);
            }))
            .step(Steps.waitUntil(ctx -> {
                FluidContainerComponent tank = getContainer(ctx.getWorld(),
                        new Vector3i(ctx.getOriginX() + 2, ctx.getOriginY(), ctx.getOriginZ()));
                return tank != null && tank.getAmount() > 0;
            }, ctx -> 5 * ctx.getWorld().getTps(), "fluid reaches tank within 5 seconds"));
}
```

### Using waitUntilOrFail to detect overshoot

```java
private static TestCase tankDoesNotOverfill() {
    return new TestCase("tank_does_not_overfill", 3, 3, 3)
            .step(Steps.run(ctx -> {
                int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                ctx.getWorld().setBlock(x, y, z, SOURCE_ID);
                ctx.getWorld().setBlock(x + 1, y, z, TANK_ID);
            }))
            .step(Steps.waitUntilOrFail(
                    ctx -> {
                        FluidContainerComponent tank = getContainer(ctx.getWorld(),
                                new Vector3i(ctx.getOriginX() + 1, ctx.getOriginY(), ctx.getOriginZ()));
                        return tank != null && tank.isFull();
                    },
                    ctx -> {
                        FluidContainerComponent tank = getContainer(ctx.getWorld(),
                                new Vector3i(ctx.getOriginX() + 1, ctx.getOriginY(), ctx.getOriginZ()));
                        return tank != null && tank.getAmount() > tank.getCapacity();
                    },
                    ctx -> 10 * ctx.getWorld().getTps(),
                    "tank fills to capacity without exceeding it"));
}
```

### Framework self-test with mutable state

```java
private static TestCase waitUntilResolves() {
    int[] count = {0};
    return new TestCase("wait_until_resolves", 1, 1, 1)
            .step(Steps.waitUntil(
                    ctx -> ++count[0] >= 5,
                    ctx -> ctx.getWorld().getTps(),
                    "counted 5 ticks"))
            .step(Steps.assertThat(
                    ctx -> count[0] >= 5,
                    "waitUntil predicate was polled at least 5 times"));
}
```

### Headless test properties

| Property | Effect |
|---|---|
| `glyphworks.test.all=true` | Run all registered modules |
| `glyphworks.test.module=<name>` | Restrict to one module |
| `glyphworks.test.suite=<name>` | Restrict to one suite within the module |
| `glyphworks.test.name=<name>` | Run a single test within the suite |
