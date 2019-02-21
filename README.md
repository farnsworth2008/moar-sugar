# Moar Sugar

Stuff to make Java sweet!

## Example #1, async methods

```java
@Override
public void run() {

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
}
```

## Example #2, database CRUD

```java
@Override
public void run() {

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
}
```

Additional Examples:
----------

See the [Moar Sugar Example App](https://github.com/farnsworth2008/moar-sugar-example/blob/master/README.md) for additional examples.
