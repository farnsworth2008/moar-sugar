# Moar Sugar

Stuff to make Java sweet!

## Overview
The idea of Moar *(pronounced like Roar)* is to make Java coding techniques a bit more modern.  The Moar Sugar project currently supports both Java 1.8 and Java 1.11.

Support for Java 1.8 will be dropped in early 2020.

Much of Moar Sugar creates *syntactical sugar* with the aim of making verbose Java coding patterns more concise or mapping common patterns to ideas that are easier to understand.  Static methods such as `safely( () -> {} ) of ` of `moar.sugar.Sugar` replace more verbose boilerplate code that is often needed.

Scheduling and obtaining asynchronous execution is an area where java is especially verbose.  With *Moar Sugar* various `$` functions provide an more concise way to schedule futures and safely get the results.

The [Hibernate](https://en.wikipedia.org/wiki/Hibernate_(framework)) framework is popular for Java products but I find it to be quite frustrating.  Often the *magic* repositories, *lazy loading*, and *attached or detached* states produce defects that are hard to debug.  Moar Sugar provides the `Waker` class and `wake(T.class) method  for working in the context of *non-magical* JDBC while also interface driven data mapping.

## Example #1, Safely Invoke Method
```java
out.println("Example: Safely invoke a method that may throw");
var result = safely(() -> methodWithException("two"));
if (has(result.thrown())) {
  out.println("we got: " + result.thrown().getMessage());
} else {
  out.println("we got: " + result.get());
}
```

## Example #2, Async Operations

```java
out.println("Example: Async Execution");

/* [require] shorthand to require everything in the block to complete
 * without exception. */
require(() -> {

  /* $ shorthand for a service using 4 threads. */
  try (var service = $(4)) {

    /* $ shorthand for a future where we get a string. */
    var futures = $(String.class);

    /* $ shorthand to run lambda(s) async */
    for (var i = 0; i < 3; i++) {
      var message1 = format("async One [%d]", i);
      var message2 = format("async Two [%d]", i);
      var message3 = format("async Three [%d]", i);
      $(service, futures, () -> methodOne(message1));
      $(service, futures, () -> methodTwo(message2), () -> methodTwo(message3));
    }

    /* $ shorthand to wait for all futures to complete */
    out.println("  async work started");
    swallow(() -> $(futures));
    out.println("  async work complete");

    /* $ shorthand to get a safe result from a future */
    var i = 0;
    for (var future : futures) {
      var result = $(future);
      var futureThrew = result.thrown() == null;
      var displayValue = futureThrew ? result.get() : result.thrown().getMessage();
      out.println(format("  futures[%d]: %s", ++i, displayValue));
    }
  }
});
out.println();
```

## Example #3, Database CRUD

```java
/**
 * Example definition for a Row.
 */
public interface PetRow
    extends
    IdColumnAsAutoLong,
    NameColumn,
    OwnerColumn,
    SpeciesColumn,
    SexColumn,
    BirthColumn,
    DeathColumn {}
```

```java
/* Works with standard javax.sql.DataSource */
var ds = getDataSource();

out.println("Example: DB");

/* Simple way to executeSql with connection open and close handled
 * automatically */
wake(ds).executeSql("delete from pet");

/* Fluent syntax style without the need for a repository of each type. */
var pet1 = wake(PetRow.class).of(ds).upsert(row -> {
  row.setName("Donut");
  row.setOwner("Mark");
  row.setSex("F");
  row.setSpecies("Dog");
  row.setBirth(toUtilDate(2015, 3, 15));
});
out.println("  upsert pet #1: " + pet1.getId() + ", " + pet1.getName());

/* Repository of each type can also be passed around. */
var repo = wake(PetRow.class).of(ds);
PetRow pet2 = repo.define();
pet2.setName("Tig");
pet2.setOwner("None");
pet2.setSex("M");
pet2.setSpecies("Dog");
pet2.setBirth(toUtilDate(2018, 4, 22));
repo.upsert(pet2);
out.println("  upsert pet #2: " + pet2.getId() + ", " + pet2.getName());
var pet2Id = pet2.getId();

/* Find based on ID is very simple and update */
var foundPet = repo.id(pet2Id).find();
out.println("  found: " + foundPet.getName());

/* Update is simply provided by the repo */
foundPet.setOwner("Mark");
repo.update(foundPet);

// Find rows using an example row to search
foundPet = repo.key(where -> {
  where.setName("Tig");
  where.setSpecies("Dog");
}).find();
out.println("  found: " + foundPet.getName() + ", " + foundPet.getOwner());

// Delete
repo.delete(foundPet);

// Upsert multiple rows.
wake(ds).upsert(PetRow.class, row -> {
  row.setName("Twyla");
  row.setOwner("Kendra");
  row.setSex("F");
  row.setSpecies("Cat");
  row.setBirth(toUtilDate(2012, 6, 5));
}, row -> {
  row.setName("Jasper");
  row.setOwner("Kendra");
  row.setSex("M");
  row.setSpecies("Cat");
  row.setBirth(toUtilDate(2012, 9, 1));
});

// Find with a where clause
var petList = repo.list("where species=?", "Cat");
for (PetRow petItem : petList) {
  out.println("  found: " + petItem.getName() + ", " + petItem.getOwner());
}
out.println();
```

Additional Examples:
----------

See the [Moar Sugar Example App](https://github.com/farnsworth2008/moar-sugar-example/blob/master/README.md) for additional examples.
