---
description: "Agent coding instructions — Error Handling. Apply these rules when writing or reviewing code."
applyTo: "**"
---

# Error Handling

Apply these principles consistently when writing, reviewing, or suggesting code changes.

## Core Rules

### Prefer exceptions over error codes
- Error codes force callers to handle errors immediately at every call site, creating deeply nested conditional chains.
- Error code enums become global dependencies — changing one value can break the entire codebase.
- Exceptions let the happy path read as a clean list of steps without nesting.

### Separate error handling from business logic
- Don't mix normal processing and error processing in the same function.
- Extract the try/catch body into its own function for the happy path, and keep error handling in a wrapper.
- A function that handles errors should do nothing else.

### Write the try-catch-finally first
- When writing code that could throw, start with the try-catch structure. This helps define the scope and expected behavior for the caller.

## Code Examples

### Prefer exceptions to nested error codes

**Avoid:**
```js
function registerUser(userData) {
  if (createAccount(userData) === ErrorCode.OK) {
    if (createProfile(userData) === ErrorCode.OK) {
      if (sendWelcomeEmail(userData.email) === ErrorCode.OK) {
        return ErrorCode.OK;
      } else {
        return ErrorCode.EMAIL_FAILED;
      }
    } else {
      return ErrorCode.PROFILE_FAILED;
    }
  } else {
    return ErrorCode.ACCOUNT_FAILED;
  }
}
```

**Prefer:**
```js
function registerUser(userData) {
  try {
    performRegistration(userData);
  } catch (error) {
    showError(error.message);
  }
}

function performRegistration(userData) {
  createAccount(userData);
  createProfile(userData);
  sendWelcomeEmail(userData.email);
  redirectToDashboard();
}
```
