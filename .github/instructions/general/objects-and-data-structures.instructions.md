---
description: "Agent coding instructions — Objects and Data Structures. Apply these rules when writing or reviewing code."
applyTo: "**"
---

# Objects and Data Structures

Apply these principles consistently when writing, reviewing, or suggesting code changes.

## Core Rules

### Objects hide data, expose behavior; data structures expose data, skip behavior
- Objects encapsulate their internals and expose what you can *do* through methods.
- Data structures (DTOs, records) expose their fields and carry no business logic.
- Never build hybrids that do both — you lose the advantages of each.

### Don't blindly add getters and setters
- Making every field private then adding a getter and setter for each one is effectively making them public again.
- Abstraction means exposing *behavior*, not *structure*. Ask "what can the caller do?" not "what fields exist?"
- An interface should let you manipulate data without knowing how it's stored (gallons? liters? electric?).

### The Law of Demeter: talk to friends, not strangers
- A method should only call methods on: its own object, objects it created, objects passed as arguments, and objects held as fields.
- Never chain through return values: `context.getOptions().getScratchDir().getAbsolutePath()` reaches three objects deep.
- If you need something from deep in a chain, tell the owning object what you need rather than navigating its internals.

### Procedural vs. object-oriented: know the tradeoff
- Procedural code (data structures + external functions) makes it easy to add new functions — no existing types change.
- Object-oriented code (objects with methods) makes it easy to add new types — no existing functions change.
- Choose based on which dimension of change you expect: new operations → procedural; new types → OO.

### Active Records are data structures, not objects
- Active Records (with `save`, `delete`) are database-row mappers. They are data structures.
- Never add business logic to Active Records. Put business rules in separate objects that consume the Active Record.

## Code Examples

### Abstract the interface, hide the implementation

**Avoid:**
```java
// Exposes storage details — caller knows it's Cartesian
public class Point {
  public double x;
  public double y;
}
```

**Prefer:**
```java
// Caller can work with the point without knowing how it's stored
public interface Point {
  double getX();
  double getY();
  void setCartesian(double x, double y);
  double getR();
  double getTheta();
  void setPolar(double r, double theta);
}
```

### Law of Demeter — ask, don't navigate

**Avoid:**
```js
const path = context.getOptions().getScratchDir().getAbsolutePath();
const file = new File(path, "output.tmp");
```

**Prefer:**
```js
const file = context.createScratchFile("output.tmp");
```

### Separate Active Record from business logic

**Avoid:**
```java
class Product extends ActiveRecord {
  private String name;
  private double price;

  void save() { ... }
  void delete() { ... }

  // Business logic mixed into data structure — hybrid!
  double applyDiscount(double pct) {
    return price * (1 - pct / 100);
  }
}
```

**Prefer:**
```java
// Active Record: pure data + persistence
class Product extends ActiveRecord {
  public String name;
  public double price;
  void save() { ... }
  void delete() { ... }
}

// Business logic lives in its own object
class PricingService {
  double applyDiscount(Product product, double pct) {
    return product.price * (1 - pct / 100);
  }
}
```

### Choose procedural or OO based on expected change

**Procedural — easy to add new functions:**
```js
function area(shape) {
  if (shape.type === "circle") return Math.PI * shape.radius ** 2;
  if (shape.type === "square") return shape.side ** 2;
}
// Adding perimeter() doesn't touch any shape
```

**Object-oriented — easy to add new types:**
```js
class Circle {
  area() { return Math.PI * this.radius ** 2; }
}
class Square {
  area() { return this.side ** 2; }
}
// Adding Triangle requires only one new class
```
