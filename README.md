# Moar Sugar

Stuff to make development sweet!

## Overview

Much of Moar Sugar, is *syntactical sugar* that makes coding more concise and easier to understand.  Static methods such as `safely( () -> {} )` replace verbose boilerplate that often gets in the way of expressing ideas.

Asynchronous execution is an area where Java is especially verbose.  With *Moar Sugar*, various `$()` functions provide a concise yet powerful syntax.

The [Hibernate](https://en.wikipedia.org/wiki/Hibernate_(framework)) framework is a popular but often frustrating.  The *magic JPA repositories, byte code tricks, lazy loading, and hidden data connections* produce defects that are often hard to debug.  Moar Sugar provides the `Waker` class and `wake(T.class) method for working with *non-magical* JDBC.  The interface driven data approach in Moar Sugar is similar to Hibernate style POJOs but less verbose and easier to define.

## Example #1, Safely Invoke Method
```java
var result = safely(() -> methodWithException("two"));
if (has(result.thrown())) {
  out.println("we got: " + result.thrown().getMessage());
} else {
  out.println("we got: " + result.get());
}
```

## Example #2, Async Operations

```java
/* $ shorthand for a future where we get a string. */
var futures = $(String.class);

/* $ shorthand to run lambda(s) async */
for (var i = 0; i < 3; i++) {
  var message1 = format("async One [%d]", i);
  var message2 = format("async Two [%d]", i);
  var message3 = format("async Three [%d]", i);

  $(service, futures, () -> methodOne(message1));
  $(service, futures, () -> methodTwo(message2));
  $(service, futures, () -> methodWithException(message3));
}

/* $ shorthand to wait for all futures to *safely* complete */
out.println("  async work started");
var results = $(futures);
out.println("  async work complete");

/* easily to walk the result list without fear of exceptions */
var i = 0;
for (var result : results) {
  var futureThrew = result.thrown() == null;
  var displayValue = futureThrew ? result.get() : result.thrown().getMessage();
  out.println(format("  result %d: %s", ++i, displayValue));
  }
}
```

## Example #3, Database CRUD

```java
/* Works with standard javax.sql.DataSource */
var ds = getDataSource();

/* Simple way to executeSql with connection open and close handled
 * automatically */
use(ds).executeSql("delete from pet");

/* Fluent syntax style without the need for a repository of each type. */
var pet1 = use(PetRow.class).of(ds).upsert(row -> {
  row.setName("Donut");
  row.setOwner("Mark");
  row.setSex("F");
  row.setSpecies("Dog");
  row.setBirth(toUtilDate(2015, 3, 15));
});
out.println(format("  %s: %s, %s", green("Upsert #1"), pet1.getId(), pet1.getName()));

/* Repository of each type can also be passed around. */
var repo = use(PetRow.class).of(ds);
PetRow pet2 = repo.define();
pet2.setName("Tig");
pet2.setOwner("None");
pet2.setSex("M");
pet2.setSpecies("Dog");
pet2.setBirth(toUtilDate(2018, 4, 22));
repo.upsert(pet2);
out.println(format("  %s: %s, %s", green("Upsert #2"), pet2.getId(), pet2.getName()));
var pet2Id = pet2.getId();

/* Find based on ID is very simple */
var foundPet = repo.id(pet2Id).find();
out.println(format("  %s: %s, %s", green("Found"), foundPet.getName(), foundPet.getOwner()));

/* Update is simply provided by the repo */
foundPet.setOwner("Mark");
repo.update(foundPet);

// Find rows using an example row to search
foundPet = repo.where(row -> {
  row.setName("Donut");
  row.setSpecies("Dog");
}).find();
out.println(format("  %s: %s, %s", green("Found"), foundPet.getName(), foundPet.getOwner()));

// Delete
repo.delete(foundPet);

// Upsert multiple rows.
use(ds).upsert(PetRow.class, row -> {
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
}, row -> {
  row.setName("Woody");
  row.setOwner("Kendra");
  row.setSex("M");
  row.setSpecies("Dog");
  row.setBirth(toUtilDate(2016, 3, 8));
});

// Find where species is cat
var petList = repo.where(row -> row.setSpecies("cat")).list();
for (PetRow petItem : petList) {
  out.println(format("  %s: %s, %s", green("Cat"), petItem.getName(), petItem.getOwner()));
}

// Find where direct sql
petList = repo.list("where species !=?", "cat");
for (PetRow petItem : petList) {
  out.println(format("  %s: %s, %s", green(petItem.getSpecies()), petItem.getName(), petItem.getOwner()));
}
```

## Example #4, ANSI Terminal Output

```bash
out.println("If you run this with -Dmoar.ansi.enabled=true you will see colors");
out.println();
out.println();
out.println(format("With %s, %s, %s, and more", red("red"), green("green"), blue("blue")));
out.println("This line will clear and rewrite in 3 seconds.");
require(() -> sleep(1000 * 3));
clearLine(out);
out.print("Rewritten line");

StatusLine progress = new StatusLine(out, "Demo Progress");
for (var i = 0; i < 100; i++) {
  swallow(() -> sleep(100));
  var completed = i;
  progress.set(() -> (float) completed / 100);
}
progress.clear();
```

# Additional Examples

See the [Moar Sugar Example App](https://github.com/moar-stuff/moar-sugar-example/blob/master/README.md) for additional examples.
