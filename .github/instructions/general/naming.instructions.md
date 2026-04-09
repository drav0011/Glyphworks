---
description: "Agent coding instructions — Naming. Apply these rules when writing or reviewing code."
applyTo: "**"
---

# Naming

Apply these principles consistently when writing, reviewing, or suggesting code changes.

## Core Rules

### Use intention-revealing names
- Every name should answer three questions: why it exists, what it does, and how to use it.
- A well-named variable needs no comment to explain its purpose.
- You write a name once but read it hundreds of times — optimize for reading.

### Avoid disinformation
- Never use names that imply a different data structure (e.g., don't call a map `accountList`).
- Avoid names that differ only in subtle ways (e.g., `XYZControllerForHandlingOfStrings` vs `XYZControllerForStorageOfStrings`).
- Avoid characters that look alike (`l` vs `1`, `O` vs `0`) in identifiers.

### Make meaningful distinctions
- If two things have different names, they must have genuinely different meanings.
- Never differentiate names with number series (`a1`, `a2`) or noise words (`Data`, `Info`, `Manager`, `Handler`).
- Class and function names must reflect distinct roles and responsibilities.

### Use pronounceable names
- If you can't say a variable name in conversation, rename it.
- Code is a social activity — names must be speakable for discussions and reviews.

### Use searchable names
- The length of a name should match the size of its scope.
- Single-letter names are acceptable only for local variables in short methods.
- Replace magic numbers and single-character constants with named constants.

### Avoid encodings
- Do not prefix variable names with type information (Hungarian notation). Modern IDEs and compilers handle types for you.
- Let names describe intent, not implementation details.

### Avoid mental mapping
- Never force readers to mentally translate a cryptic name into its real meaning.
- The reader's brain should focus on logic, not act as a lookup table for abbreviations.

## Code Examples

### Intention-revealing names

**Avoid:**
```js
let d;
d = getElapsedTimeInDays();
if (d > 30) {
  sendReminderEmail(d);
  logActivity(d);
}
```

**Prefer:**
```js
let elapsedTimeInDays = getElapsedTimeInDays();
if (elapsedTimeInDays > 30) {
  sendReminderEmail(elapsedTimeInDays);
  logActivity(elapsedTimeInDays);
}
```

### Searchable names over magic numbers

**Avoid:**
```js
for (let e = 0; e < 7; e++) {
  total += (data[e] * 4) / 5;
}
```

**Prefer:**
```js
const WORK_DAYS_PER_WEEK = 5;
const MAX_CLASSES_PER_STUDENT = 7;
for (let i = 0; i < MAX_CLASSES_PER_STUDENT; i++) {
  total += (data[i] * 4) / WORK_DAYS_PER_WEEK;
}
```

### Pronounceable names

**Avoid:**
```js
let genymdhms = new Date();
let prcssr = new Processor();
let usrRcrds = [];
```

**Prefer:**
```js
let generationTimestamp = new Date();
let processor = new Processor();
let userRecords = [];
```

### Replace mental mapping with clarity

**Avoid:**
```js
function calc(p, t, q) {
  return (p + t) * q;
}
```

**Prefer:**
```js
function calculateTotal(price, tax, quantity) {
  return (price + tax) * quantity;
}
```
