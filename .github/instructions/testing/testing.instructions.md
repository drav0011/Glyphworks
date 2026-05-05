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
- `TestRunnerSystem` advances tests concurrently across the queue, while each individual test still runs its own steps in order.

### Structure tests as TestCase with sequential steps
- A `TestCase` declares a name, a bounding box size (`areaWidth`, `areaDepth`, `areaHeight`), and an ordered list of `TestStep`s.
- Steps execute sequentially per test — one per tick unless a `wait` extends it.
- Different tests in the same run can be in different phases at the same tick; avoid cross-test state dependencies.
- If any step returns `StepResult.failed(reason)`, remaining steps are skipped and the test fails.
- Chain steps via the builder: `new TestCase("name", w, d, h).step(...).step(...).afterFinish(...)`.

### Use suite lifecycle hooks for shared setup and teardown
- `beforeAll` runs once before any test in the suite.
- `beforeEach` runs for every test before test steps.
- `afterFinish` runs for every test after test steps complete (pass or fail).
- `afterEach` runs for every test after `afterFinish`.
- `afterAll` runs once after all tests in the suite are terminal.

### Use the Steps factory for all step types
- `Steps.run(ctx -> ...)` — execute an action synchronously, returns `DONE` immediately.
- `Steps.wait(ticks)` — pause for exactly N ticks.
- `Steps.wait(ctx -> ticks)` — pause for a computed number of ticks (e.g., `ctx -> 2 * ctx.getWorld().getTps()` for 2 seconds).
- `Steps.assertThat(ctx -> predicate, description)` — pass if predicate is true, fail with description otherwise.
- `Steps.succeedWhen(predicate, maxTicks, description)` — poll predicate every tick; pass on true, fail after maxTicks.
- `Steps.succeedWhen(predicate, ctx -> maxTicks, description)` — same as above with runtime tick budget.
- `Steps.succeed()` / `Steps.succeedIf(...)` — immediate success helpers.
- `Steps.fail(...)` / `Steps.failIf(...)` — immediate failure helpers.
- `Steps.afterFinish(ctx -> ...)` — test finalizer hook step.

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

### Group tests into TestSuites, register via TestRegistrations
- A `TestSuite` is a named collection of `TestCase`s: `new TestSuite("suite_id").test(case1).test(case2)`.
- Each module gets a `*TestRegistrations` class (e.g., `FluidTestRegistrations`) with a `registerAll()` method that calls `TestCase.register(moduleId, suite)` for each suite.
- The central `TestRegistrations.registerAll()` in the test plugin calls each module's `*TestRegistrations.registerAll()`.
- Suite IDs use `snake_case`, test names use `snake_case`.

### One test class per domain concern
- Each test class is a static utility with a `register(moduleId)` method and a private `buildSuite()` method.
- Test methods are private static factories returning `TestCase`.
- Name the class after what it tests: `FluidSourceSystemTests`, `GridConnectionTests`.

### Organize tests by module domain
- Keep tests grouped by module package (grid, fluid, item, crafting, smoke).
- Shared test utilities (`FluidTestUtil`, `ItemTestUtil`, etc.) stay near the owning module tests and must be `public`.
- Component behavior tests are still first-class tests in the current framework.
- Persistence-only test suites are not currently maintained.

### Separate system and component tests when useful
- System tests validate live world behavior and ECS ticking outcomes.
- Component tests validate component data behavior (copy semantics, face/link logic, helper behavior) without introducing persistence-only setup.
- Use package organization that keeps both test types easy to discover within each module.

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
    .\gradlew compileTestJava
    $env:JAVA_TOOL_OPTIONS="-Dglyphworks.test.module=<module>" ; ./gradlew runTestServer ; Remove-Item Env:JAVA_TOOL_OPTIONS
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

### Registering tests for a module

Each module gets its own `*TestRegistrations` class that collects all suite registrations:

```java
public final class FluidTestRegistrations {

    private FluidTestRegistrations() {
    }

    public static void registerAll() {
        FluidSourceSystemTests.register("fluid");
        FluidGridTransferTests.register("fluid");
        FluidSinkSystemTests.register("fluid");
    }
}
```

The central `TestRegistrations` in the test plugin calls each module in one place:

```java
public final class TestRegistrations {

    private TestRegistrations() {
    }

    public static void registerAll() {
        TestFrameworkTests.register("smoke");
        GridTestRegistrations.registerAll();
        FluidTestRegistrations.registerAll();
        ItemTestRegistrations.registerAll();
        CraftingTestRegistrations.registerAll();
    }
}
```

### Directory and package layout

```
fluid/tests/
    FluidTestUtil.java              ← public
    system/
        FluidGridTransferTests.java
        FluidSourceSystemTests.java
        ...

item/tests/
    ItemTestUtil.java               ← public
    system/
        ItemGridTransferTests.java
        ItemSourceSystemTests.java
        ...

grid/tests/
    GridTestUtil.java               ← public
    system/
        GridGraphTests.java
        GridBlockChangeTests.java
        GridConnectionTests.java
        PipeConnectionTests.java
    component/
        GridComponentTests.java
        GridFaceUtilTests.java
```

### Using succeedWhen for async system convergence

```java
private static TestCase pipeTransfersFluid() {
    return new TestCase("pipe_transfers_fluid", 5, 3, 1)
            .step(Steps.run(ctx -> {
                int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                ctx.getWorld().setBlock(x, y, z, SOURCE_ID);
                ctx.getWorld().setBlock(x + 1, y, z, PIPE_ID);
                ctx.getWorld().setBlock(x + 2, y, z, TANK_ID);
            }))
            .step(Steps.succeedWhen(ctx -> {
                FluidContainerComponent tank = getContainer(ctx.getWorld(),
                        new Vector3i(ctx.getOriginX() + 2, ctx.getOriginY(), ctx.getOriginZ()));
                return tank != null && tank.getAmount() > 0;
            }, ctx -> 5 * ctx.getWorld().getTps(), "fluid reaches tank within 5 seconds"));
}
```

### Using succeedWhen and failIf for bounded convergence

```java
private static TestCase tankDoesNotOverfill() {
    return new TestCase("tank_does_not_overfill", 3, 3, 3)
            .step(Steps.run(ctx -> {
                int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                ctx.getWorld().setBlock(x, y, z, SOURCE_ID);
                ctx.getWorld().setBlock(x + 1, y, z, TANK_ID);
            }))
            .step(Steps.succeedWhen(ctx -> {
                FluidContainerComponent tank = getContainer(ctx.getWorld(),
                        new Vector3i(ctx.getOriginX() + 1, ctx.getOriginY(), ctx.getOriginZ()));
                return tank != null && tank.isFull();
            }, ctx -> 10 * ctx.getWorld().getTps(), "tank fills to capacity"))
            .step(Steps.failIf(ctx -> {
                FluidContainerComponent tank = getContainer(ctx.getWorld(),
                        new Vector3i(ctx.getOriginX() + 1, ctx.getOriginY(), ctx.getOriginZ()));
                return tank != null && tank.getAmount() > tank.getCapacity();
            }, "tank exceeded capacity"));
}
```

### Framework self-test with mutable state

```java
private static TestCase succeedWhenResolves() {
    int[] count = {0};
    return new TestCase("succeed_when_resolves", 1, 1, 1)
        .step(Steps.succeedWhen(
                    ctx -> ++count[0] >= 5,
                    ctx -> ctx.getWorld().getTps(),
                    "counted 5 ticks"))
            .step(Steps.assertThat(
                    ctx -> count[0] >= 5,
            "succeedWhen predicate was polled at least 5 times"));
}
```

### Headless test properties

| Property | Effect |
|---|---|
| `glyphworks.test.all=true` | Run all registered modules |
| `glyphworks.test.module=<name>` | Restrict to one module |
| `glyphworks.test.suite=<name>` | Restrict to one suite within the module |
| `glyphworks.test.name=<name>` | Run a single test within the suite |
