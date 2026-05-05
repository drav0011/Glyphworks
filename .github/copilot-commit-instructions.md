# Commit Message Instructions

## Format
Use [Conventional Commits](https://www.conventionalcommits.org/) with the following format:

```
<type>(scope): <short description>

<detailed description>
```

## Type
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `style`: Code style changes (formatting, missing semicolons, etc.)
- `refactor`: Code refactoring
- `perf`: Performance improvements
- `test`: Adding or updating tests
- `build`: Build system or dependency changes
- `ci`: CI/CD configuration changes
- `chore`: Other changes that don't modify src or test files

## Scope
**REQUIRED** - Use the module or domain name:
- `grid` - Grid network system (graph, nodes, edges, tick pipeline)
- `fluid` - Fluid module (tanks, pipes, containers)
- `item` - Item grid module (inserters, extractors, storage)
- `crafting` - Crafting module
- `transfer` - Transfer logic shared across grid types
- `test` - In-game test framework and test cases
- `assets` - Block/item JSON configs, models, textures
- `build` - Build system or Gradle configuration
- `ci` - CI/CD configuration
- `root` - Root-level files (settings.gradle.kts, README.md, etc.)

## Title (First Line)
- **Keep it short** (50 characters or less)
- Use imperative mood ("add feature" not "added feature")
- Don't end with a period
- Lowercase after the colon

## Description (Body)
- **Add details here** - explain what and why, not how
- Wrap at 72 characters
- Use bullet points for multiple changes
- Reference issues if applicable

## Examples

### Feature
```
feat(fluid): add fluid pipe block with bidirectional transfer

- Add Glyphworks_Fluid_Pipe item JSON and model assets
- Register pipe grid component with bidirectional face planes
- Hook into GridTypeHandler tick for fluid distribution
```

### Fix
```
fix(grid): repair extractor/inserter tick after chunk reload

originPosition is transient and was not repopulated by
ChunkLoadGridGraphEvent, causing extractors to silently skip ticks.
```

### Refactor
```
refactor(grid): extract GridLookup from GridSystem

Separates position resolution from tick logic for clarity.
```

### Test
```
test(fluid): add tank fill and drain integration tests

Cover single-tank fill, pipe chain distribution, and empty drain.
```

### Assets
```
assets(fluid): add pipe and tank block models and item JSONs
```

### Build
```
build(root): update Gradle wrapper to 8.7
```
