---
description: "Agent coding instructions — Functions. Apply these rules when writing or reviewing code."
applyTo: "**"
---

# Functions

Apply these principles consistently when writing, reviewing, or suggesting code changes.

## Core Rules

### Keep functions small
- Functions should be short — ideally 5–15 lines.
- Blocks inside `if`, `else`, `while`, and `for` should be one line long, typically a function call.
- If a function needs explaining, it's too long. Extract until it tells a story.

### Do one thing
- A function should have one reason to change, one clear purpose.
- If you can divide a function into labeled sections, each section is a separate responsibility — extract them.
- If you can extract another function from it with a name that isn't just a restatement of the implementation, the original is doing more than one thing.

### One level of abstraction per function
- Each function should operate at a single level of abstraction.
- Code should read top-down like a narrative: high-level intent at the top, details deeper down.
- A function that mixes high-level orchestration with low-level implementation is a micro-manager — delegate the details.

### Use descriptive names
- Function names must make meaningful distinctions — if two functions have different names, they must do different things.
- Avoid noise suffixes like `Data`, `Info`, `Manager`, `Handler` when they don't add meaning.
- Name functions so they read like sentences describing what they do.

### Minimize arguments
- Zero arguments is ideal. One is clear. Two is acceptable. Three or more is a signal to refactor.
- When arguments pile up, they usually want to become an object — group related values and pass the concept.
- Avoid flag (boolean) arguments — they signal the function does two different things. Split into two functions instead.

### Avoid side effects
- A function must do only what its name promises. Hidden state changes are bugs waiting to happen.
- If a function named `checkPassword` also starts a session, it lies. Rename it or split the responsibilities.
- Data should flow in through arguments and out through return values, not through hidden mutations.

### Prefer output arguments as return values
- Don't modify input parameters to produce output. Return new values instead.
- Readers expect arguments as input and return values as output — don't break that expectation.

### Use polymorphism instead of switch statements
- Switch statements do N things by nature. Bury them in factories to create objects, then let polymorphism handle dispatch.
- When you add a new type, you should only need to add one new class — not update every switch in the codebase.

### Don't repeat yourself (DRY)
- Every piece of knowledge must have a single, authoritative representation in the system.
- If changing a behavior requires updating multiple places, extract the shared logic into one function.

### Clean code is rewritten, not written
- Nobody writes clean functions on the first try. Get working code first, then refactor: split functions, find better names, eliminate duplication.
- Refactor with confidence by maintaining test coverage.

## Code Examples

### Extract until the function tells a story

**Avoid:**
```js
function uploadFile(file, userId) {
  const filePath = `uploads/${userId}/${file.name}`;
  if (file.size > MAX_SINGLE_UPLOAD_SIZE) {
    const totalChunks = Math.ceil(file.size / CHUNK_SIZE);
    const uploadId = Storage.initMultipartUpload(filePath);
    for (let i = 0; i < totalChunks; i++) {
      const start = i * CHUNK_SIZE;
      const end = Math.min(start + CHUNK_SIZE, file.size);
      const chunk = file.slice(start, end);
      Storage.uploadPart(uploadId, i, chunk);
    }
    Storage.completeMultipartUpload(uploadId);
  } else {
    Storage.upload(filePath, file);
  }
}
```

**Prefer:**
```js
function uploadFile(file, userId) {
  const filePath = buildFilePath(userId, file.name);
  if (file.size > MAX_SINGLE_UPLOAD_SIZE) {
    uploadInChunks(filePath, file);
  } else {
    Storage.upload(filePath, file);
  }
}
```

### Replace switch with polymorphism

**Avoid:**
```js
function calculatePay(employee) {
  switch (employee.type) {
    case "fullTime":
      return employee.salary / 12;
    case "partTime":
      return employee.hours * employee.rate;
    case "contractor":
      return employee.hours * employee.contractRate;
  }
}
```

**Prefer:**
```js
// Factory buries the switch; each type owns its logic
function createEmployee(record) {
  switch (record.type) {
    case "fullTime":   return new FullTimeEmployee(record);
    case "partTime":   return new PartTimeEmployee(record);
    case "contractor": return new Contractor(record);
  }
}

// Calling code — no switch needed
const pay = employee.calculatePay();
```

### Avoid flag arguments

**Avoid:**
```js
function createUser(isAdmin) {
  if (isAdmin) {
    setupAdminAccount();
  } else {
    setupMemberAccount();
  }
}
```

**Prefer:**
```js
function createAdmin() {
  setupAdminAccount();
}

function createMember() {
  setupMemberAccount();
}
```

### Group related arguments into objects

**Avoid:**
```js
function createCircle(x, y, radius, color, lineWidth) { ... }
```

**Prefer:**
```js
function createCircle({ center, radius, style }) { ... }
```

### Eliminate side effects

**Avoid:**
```js
function checkPassword(user, password) {
  if (user.password === hash(password)) {
    Session.initialize(); // hidden side effect!
    return true;
  }
  return false;
}
```

**Prefer:**
```js
function checkPassword(user, password) {
  return user.password === hash(password);
}
// Handle session separately at the call site
if (checkPassword(user, password)) {
  Session.initialize();
}
```
