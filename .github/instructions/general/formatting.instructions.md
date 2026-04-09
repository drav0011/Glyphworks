---
description: "Agent coding instructions — Formatting. Apply these rules when writing or reviewing code."
applyTo: "**"
---

# Formatting

Apply these principles consistently when writing, reviewing, or suggesting code changes.

## Core Rules

### The newspaper metaphor
- A source file should read like a newspaper article: the headline (highest-level function) at the top, details increasing as you scroll down.
- Place each function just below its caller so code reads in a natural top-down flow.

### Vertical openness between concepts
- Use blank lines to separate distinct concepts — imports, fields, each function, logical blocks within a function.
- Every blank line signals "new concept starts here" and enables scanning over line-by-line reading.

### Vertical density for related code
- Lines that are tightly related should appear close together without blank lines between them.
- Don't space out every line equally — group related declarations and operations into cohesive blocks.
- Data extraction at the top, business logic in the middle, result at the end — each group with a single clear purpose.

### Vertical distance and ordering
- Declare variables as close to their usage as possible. A variable declared far above its use becomes mental baggage.
- Local variables go inside the block where they're used, not at the top of the function.
- Class properties that are shared across methods belong in one designated place (typically the top of the class).

### Conceptual affinity
- Functions that serve a similar purpose should be grouped together, even if one doesn't call the other.
- Shared naming patterns (e.g., `formatDate`, `formatScore`) signal conceptual siblings — keep them adjacent.

### Indentation
- Each scope level gets its own indent. Code becomes a visual hierarchy navigable at a glance.

### Always use top-level imports
- All `import` statements belong at the top of the file, before the class declaration.
- Never reference a type by its fully-qualified name inline (e.g., `new java.util.HashSet<>()`) — import it and use the simple name.
- Never use wildcard imports (`import java.util.*`). Import each type explicitly so dependencies are visible at a glance.

### Team rules win
- Formatting conventions (braces, tabs vs spaces, quote style) are team decisions. Consistency across the codebase matters more than individual preference.

## Code Examples

### Vertical openness separates concepts

**Avoid:**
```js
const db = connectDatabase();
const users = db.query("SELECT * FROM users");
const report = generateReport(users);
const formatted = formatReport(report);
sendEmail(formatted);
logAction("report_sent");
```

**Prefer:**
```js
const db = connectDatabase();
const users = db.query("SELECT * FROM users");

const report = generateReport(users);
const formatted = formatReport(report);

sendEmail(formatted);
logAction("report_sent");
```

### Declare variables close to usage

**Avoid:**
```js
function processData(items) {
  let skippedLog = [];
  let report;
  // ... 30 lines of processing ...
  if (condition) {
    skippedLog.push(item);
  }
  // ... 20 more lines ...
  report = buildReport(results);
  return report;
}
```

**Prefer:**
```js
function processData(items) {
  // ... processing ...
  if (condition) {
    const skippedLog = [];
    skippedLog.push(item);
  }
  // ... more processing ...
  const report = buildReport(results);
  return report;
}
```
