---
description: "Agent coding instructions — Comments. Apply these rules when writing or reviewing code."
applyTo: "**"
---

# Comments

Apply these principles consistently when writing, reviewing, or suggesting code changes.

## Core Rules

### Prefer self-documenting code over comments
- Before writing a comment, try to refactor the code so the comment becomes unnecessary.
- Extract complex conditions into well-named variables or functions instead of explaining them with comments.
- Replace magic numbers with named constants rather than commenting their meaning.

### Good comments: explain WHY, not WHAT
- Use comments to explain intent, business context, or constraints that the code cannot express.
- Warn other developers about consequences (e.g., "this test is slow", "this class is not thread-safe").
- Use TODO comments for genuine constraints that block the ideal solution, not for deferred laziness.
- Use clarifying comments when dealing with non-intuitive APIs (e.g., compareTo return values).

### Bad comments: redundancy and noise
- Never write comments that restate what the code already says. Redundant comments train developers to skip all comments.
- Don't add mandated doc-comments that exist only to satisfy a rule — they add clutter and misdirection potential.
- If a comment requires its own explanation to be understood, it has failed its purpose.

### Bad comments: misleading and dangerous
- An inaccurate comment is worse than no comment at all — it actively deceives.
- Never write a comment about code you don't control. If a default is defined elsewhere, the comment will go stale.
- If a comment gives the reader false confidence to stop reading the implementation, it's harmful.

### Delete commented-out code
- Never leave commented-out code in the codebase. Version control preserves every line ever written.
- Remove author attributions and dated journal entries — `git log` does that better.
- Your code should only contain what runs today.

### Public documentation follows patterns
- For public APIs and libraries, document: what it does, what goes in, what comes out, what can go wrong, and a usage example.
- For legal requirements, reference a standard license file rather than embedding full text.
- Keep license headers at the top of files where IDEs can collapse them.

### Keep comments readable
- Write comments in plain text, not HTML. You read comments in your editor, not a browser.
- Don't mumble — if you're unsure about something, write a specific TODO with the actual constraint.
- Keep comments local: a comment should only describe the code immediately next to it.

## Code Examples

### Refactor instead of commenting

**Avoid:**
```js
// Check if employee is eligible for benefits
if (employee.type === "fullTime" && employee.tenure > 1 && !employee.onProbation) {
  ...
}
```

**Prefer:**
```js
const isEligibleForBenefits =
  employee.type === "fullTime" &&
  employee.tenure > 1 &&
  !employee.onProbation;

if (isEligibleForBenefits) {
  ...
}
```

### Good comment: explaining business intent

**Prefer:**
```js
// Free-tier users are limited to 3 projects. This is a business decision to
// drive upgrades, not a technical limitation — do not "optimize" this away.
const MAX_FREE_PROJECTS = 3;
```

### Good comment: warning about consequences

**Prefer:**
```js
// SimpleDateFormat is not thread-safe. Creating a shared instance
// causes intermittent concurrency bugs under load.
const formatter = new SimpleDateFormat("yyyy-MM-dd");
```

### Never leave commented-out code

**Avoid:**
```js
function processOrder(order) {
  validateStock(order);
  // calculateLegacyDiscount(order);
  // if (order.isVIP) { applyVIPPricing(order); }
  calculateBill(order);
  chargeCustomer(order);
}
```

**Prefer:**
```js
function processOrder(order) {
  validateStock(order);
  calculateBill(order);
  chargeCustomer(order);
}
// If you need the old code, check git history.
```
