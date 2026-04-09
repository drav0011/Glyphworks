---
description: "Agent coding instructions — Commands. Apply these rules when writing or reviewing Hytale plugin code."
applyTo: "**/*.java"
---

# Commands

Apply these principles consistently when writing, reviewing, or suggesting code changes.

## Core Rules

### Pick the right command base class
- `AbstractAsyncCommand` — runs on a background thread. Cannot safely access `Store` or `Ref`. Use for actions not tied to a specific world (server info, rules, announcements).
- `AbstractPlayerCommand` — runs on the world thread. Has full access to `Store<EntityStore>`, `Ref<EntityStore>`, `PlayerRef`, and `World`. Use for most player-facing commands.
- `AbstractTargetPlayerCommand` — like `AbstractPlayerCommand` but adds a `--player <name>` argument. Use when the command should target another player.
- `AbstractTargetEntityCommand` — uses a raycast to find the entity the player is looking at. Use for entity-inspection or entity-manipulation commands.
- `AbstractWorldCommand` — runs on the world thread with access to `World` and `Store`. Use for world-level operations not tied to a specific player entity.
- `AbstractCommandCollection` — groups sub-commands under a shared prefix. The collection itself has no execute logic.

### Declare arguments in the constructor
- Use `withRequiredArg(name, description, ArgTypes.TYPE)` for mandatory positional arguments parsed left to right.
- Use `withOptionalArg(name, description, ArgTypes.TYPE)` for named arguments with `--name <value>` syntax.
- Use `withDefaultArg(name, description, ArgTypes.TYPE, defaultValue, defaultDescription)` for optional args with a fallback.
- Use `withFlagArg(name, description)` for boolean toggles with `--name` syntax.
- Declare args in most-specific → most-general order so the usage text reads naturally.

### Retrieve argument values at execute time
- Call `arg.get(commandContext)` inside the `execute` method to get the value.
- `OptionalArg` returns `null` when not provided. `FlagArg` returns `true` or `false`.
- `DefaultArg` returns the default value when not provided.

### Name commands with a namespace prefix
- Primary command name: `"modname:command"` (e.g., `"glyphworks:test"`).
- Register a short alias via `addAliases("gw:test")` in the constructor.
- This avoids collisions with other mods and vanilla commands.

### Use permissions for access control
- Call `requirePermission(HytalePermissions.fromCommand("permissionName"))` in the constructor.
- Multiple `requirePermission` calls require the player to have all listed permissions.
- Use `PermissionRules.or(perm1, perm2)` when the player needs any one from a set.

### Use variants for command overloads
- Call `addUsageVariant(new SubCommand())` in the parent command's constructor.
- The variant's constructor calls `super("Description only")` — no command name.
- Use variants instead of flag arguments when two execution paths are fundamentally different.

### Use command collections for hierarchical commands
- Extend `AbstractCommandCollection` and call `addSubCommand(new ChildCommand())` in the constructor.
- Collections can nest: a collection can contain other collections.

### Add validators to arguments
- Chain `.addValidator(Validators.greaterThan(0))` after the `withXxxArg` call.
- Validators run before `execute` and produce clear error messages to the user.
- Custom validators implement `Validator<T>` with an `accept(value, results)` method.

### Register commands during setup
- Call `plugin.getCommandRegistry().registerCommand(new MyCommand())` in the plugin's `setup()` method.

## Code Examples

### Standard player command with arguments

```java
public class HealCommand extends AbstractTargetPlayerCommand {

    private final DefaultArg<Float> healthArg;
    private final OptionalArg<String> messageArg;
    private final FlagArg verboseArg;

    public HealCommand() {
        super("mymod:heal", "Heal a player");
        addAliases("mm:heal");

        this.healthArg = withDefaultArg("health", "Amount to heal",
                ArgTypes.FLOAT, 100f, "Default: 100");
        this.messageArg = withOptionalArg("message", "Message to display",
                ArgTypes.STRING);
        this.verboseArg = withFlagArg("verbose", "Enable debug output");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
            @Nullable Ref<EntityStore> targetRef,
            @Nonnull Ref<EntityStore> senderRef,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world,
            @Nonnull Store<EntityStore> store) {

        float amount = healthArg.get(context);
        String message = messageArg.get(context);

        EntityStatMap stats = store.getComponent(targetRef, EntityStatMap.getComponentType());
        int healthIdx = DefaultEntityStatTypes.getHealth();
        stats.addStatValue(healthIdx, amount);

        if (message != null) {
            context.sendMessage(Message.raw(message));
        }
    }
}
```

### Async command — no world thread access

```java
public class RulesCommand extends AbstractAsyncCommand {

    public RulesCommand() {
        super("mymod:rules", "Display server rules");
        addAliases("mm:rules");
    }

    @Override
    protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext context) {
        context.sendMessage(Message.raw("1. Be respectful"));
        context.sendMessage(Message.raw("2. No griefing"));
        return CompletableFuture.completedFuture(null);
    }
}
```

### Command collection with sub-commands

```java
public class AdminCommands extends AbstractCommandCollection {

    public AdminCommands() {
        super("mymod:admin", "Admin commands");
        addAliases("mm:admin");
        addSubCommand(new AdminKickCommand());
        addSubCommand(new AdminBanCommand());
    }
}
```

### Argument validation

```java
this.amountArg = withRequiredArg("amount", "Transfer amount", ArgTypes.INTEGER)
        .addValidator(Validators.greaterThan(0))
        .addValidator(Validators.lessThan(1000));
```

### Permissions

```java
public MyCommand() {
    super("mymod:dangerous", "A restricted command");
    requirePermission(HytalePermissions.fromCommand("admin"));
    requirePermission(
        PermissionRules.or(
            HytalePermissions.fromCommand("moderator"),
            HytalePermissions.fromCommand("owner")
        )
    );
}
```
