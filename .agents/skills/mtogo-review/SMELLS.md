# Smell baseline

A fixed set of Fowler code smells (_Refactoring_, ch. 3) that applies on top of the documents:

- **The documents override.** Where a documented rule endorses something a smell would flag, the smell is suppressed. "A new feature copies the shape of an existing one" and "share only what is shared" are such rules.
- **Always a judgement call.** Each smell is a labelled heuristic ("possible Feature Envy"), never a hard violation. Skip anything tooling already enforces (formatting, lint, spelling).

Each smell reads _what it is_ → _how to fix_:

- **Mysterious Name**: a name that doesn't reveal what it does or holds. → rename it; if no honest name comes, the design is murky.
- **Duplicated Code**: the same logic shape in more than one hunk or file. → extract the shared shape, call it from both.
- **Feature Envy**: a method that reaches into another object's data more than its own. → move it onto the data it envies.
- **Data Clumps**: the same few fields or parameters travelling together. → bundle them into one type.
- **Primitive Obsession**: a primitive or string standing in for a domain concept. → give the concept its own small type.
- **Repeated Switches**: the same `switch` or `if` cascade on the same type in several places. → polymorphism, or one shared map.
- **Shotgun Surgery**: one logical change forces scattered edits across many files. → gather what changes together.
- **Divergent Change**: one file edited for several unrelated reasons. → split so each changes for one reason.
- **Speculative Generality**: abstractions or hooks for needs the spec doesn't have. → delete and inline until a real need shows.
- **Message Chains**: long `a.b().c().d()` navigation. → hide the walk behind one method.
- **Middle Man**: a class that mostly delegates onward. → cut it, call the target directly.
- **Refused Bequest**: a subclass that ignores most of what it inherits. → use composition.
