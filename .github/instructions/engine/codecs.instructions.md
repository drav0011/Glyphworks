---
description: "Agent coding instructions — Codecs and Serialization. Apply these rules when writing or reviewing Hytale plugin code."
applyTo: "**/*.java"
---

# Codecs and Serialization

Apply these principles consistently when writing, reviewing, or suggesting code changes.

## Core Rules

### Use BuilderCodec for all serializable types
- Every component or data class that is saved to disk or sent over the network needs a `public static final BuilderCodec<T> CODEC`.
- The builder pattern is: `BuilderCodec.builder(Class, factory).append(keyedCodec, setter, getter).add()...build()`.
- The factory (first arg to `builder`) is typically the no-arg constructor reference: `MyComponent::new`.

### KeyedCodec identifiers must be globally unique
- Every identifier string passed to `new KeyedCodec<>(identifier, codec)` must start with an uppercase letter.
- Identifiers must be unique across the entire mod — not just within the component. Collisions cause silent serialization corruption.
- Use the pattern `ModName_ClassName_FieldName` to guarantee uniqueness — e.g., `MyMod_FluidContainer_Capacity`.
- The mod-name prefix prevents collisions across mods; the class-name segment prevents collisions within the same mod.

### Match the codec to the field type
- Primitive codecs: `Codec.STRING`, `Codec.BOOLEAN`, `Codec.BYTE`, `Codec.SHORT`, `Codec.INTEGER`, `Codec.LONG`, `Codec.FLOAT`, `Codec.DOUBLE`.
- Array codecs: `Codec.INT_ARRAY`, `Codec.LONG_ARRAY`, `Codec.FLOAT_ARRAY`, `Codec.DOUBLE_ARRAY`, `Codec.STRING_ARRAY`.
- UUID: `Codec.UUID_BINARY` (compact) or `Codec.UUID_STRING` (human-readable).
- Time: `Codec.INSTANT`, `Codec.DURATION`, `Codec.DURATION_SECONDS`.
- Enums: `new EnumCodec<>(MyEnum.class)`.
- Collections: `new SetCodec<>(elementCodec, HashSet::new, false)`, `new MapCodec<>(valueCodec, HashMap::new, false)`.
- Nested objects: use the nested type's own `CODEC` (e.g., `FacePlane.CODEC`).

### Setter and getter lambdas must be symmetric
- The setter `(data, value) -> data.field = value` and getter `data -> data.field` must target the exact same field.
- For registry-backed fields, the getter serializes the ID string and the setter deserializes via registry lookup.

### Add validators at the codec level
- Use `Validators.nonNull()` for fields that must never be null.
- Use `Validators.greaterThan(n)`, `Validators.lessThan(n)`, `Validators.range(min, max)` for numeric bounds.
- Chain validators: `.addValidator(Validators.nonNull()).addValidator(Validators.greaterThan(0))`.
- Create custom validators by implementing `Validator<T>` when built-in validators are insufficient.

### Handle registry-backed fields with ID serialization
- When a field refers to a registry entry (e.g., a `GridType`), serialize as the string ID and deserialize via the registry.
- This decouples the serialized format from runtime object identity.

### Runtime-only types do not need a codec
- Components that are never persisted (e.g., test runner state, temporary markers) can skip the codec entirely.
- Pass an empty codec or `null` when registering runtime-only components if the API requires it.

## Code Examples

### Basic BuilderCodec with primitives

```java
public static final BuilderCodec<PoisonComponent> CODEC = BuilderCodec
        .builder(PoisonComponent.class, PoisonComponent::new)
        .append(
                new KeyedCodec<>("MyMod_Poison_DamagePerTick", Codec.FLOAT),
                (c, v) -> c.damagePerTick = v,
                c -> c.damagePerTick)
        .add()
        .append(
                new KeyedCodec<>("MyMod_Poison_RemainingTicks", Codec.INTEGER),
                (c, v) -> c.remainingTicks = v,
                c -> c.remainingTicks)
        .add()
        .build();
```

### Enum codec

```java
.append(
        new KeyedCodec<>("MyMod_FacePlane_Normal", new EnumCodec<>(BlockFace.class)),
        (c, v) -> c.normal = v,
        c -> c.normal)
.add()
```

### Collection codecs (Set, Map)

```java
// Set of nested objects
.append(
        new KeyedCodec<>("MyMod_GridComponent_Faces",
                new SetCodec<>(FacePlane.CODEC, HashSet::new, false)),
        (c, v) -> c.faces = v,
        c -> c.faces)
.add()

// Map with string keys
.append(
        new KeyedCodec<>("MyMod_StatsComponent_Values",
                new MapCodec<>(Codec.FLOAT, HashMap::new, false)),
        (c, v) -> c.values = v,
        c -> c.values)
.add()
```

### Registry-backed field serialization

```java
// GridType is resolved at runtime via registry — serialize as string ID
.append(
        new KeyedCodec<>("MyMod_GridEntry_Type", Codec.STRING),
        (c, v) -> c.gridType = GridTypeRegistry.get(v),
        c -> c.gridType != null ? c.gridType.id() : null)
.add()
```

### Adding validators

```java
.append(
        new KeyedCodec<>("MyMod_FluidTank_Capacity", Codec.INTEGER),
        (c, v) -> c.capacity = v,
        c -> c.capacity)
.addValidator(Validators.nonNull())
.addValidator(Validators.greaterThan(0))
.add()
```

### Identifier collision — the silent corruption bug

**Avoid:**
```java
// In ComponentA:
new KeyedCodec<>("Capacity", Codec.INTEGER)
// In ComponentB:
new KeyedCodec<>("Capacity", Codec.INTEGER) // COLLISION — silent corruption
```

**Prefer:**
```java
// In ComponentA:
new KeyedCodec<>("MyMod_FluidContainer_Capacity", Codec.INTEGER)
// In ComponentB:
new KeyedCodec<>("MyMod_ItemContainer_Capacity", Codec.INTEGER)
```
