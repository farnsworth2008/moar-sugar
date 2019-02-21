# Moar Sugar

Stuff to make Java sweet!

## Example #1, async methods

```java
  @Override
  public void run() {
    out.println("Example: Async Execution");
    var service = $(4);
    try {
      var futures = $(String.class);

      for (var i = 0; i < 3; i++) {
        var message = "async " + i;
        $(service, futures, () -> methodOne(message));
      }

      for (var i = 0; i < 3; i++) {
        var message = "async " + i;
        $(service, futures, () -> methodTwo(message));
      }

      for (var i = 0; i < 3; i++) {
        var message = "async " + i;
        $(service, futures, () -> methodWithRetryableException(message));
      }

      out.println("  async work started");
      swallow(() -> $(futures));
      out.println("  async work complete");

      var i = 0;
      for (var future : futures) {
        var result = safely(() -> future.get());
        var displayValue = result.thrown() == null ? result.get() : result.thrown().getMessage();
        out.println(format("  futures[%d]: %s", ++i, displayValue));
      }

    } finally {
      service.shutdown();
    }
    out.println();
  }
```

## Example #2, database CRUD

```java
  @Override
  public void run() {
    var ds = getDataSource();

    out.println("Example: DB");

    // simple way to executeSql against a DataSource
    wake(ds).executeSql("delete from pet");

    // style 1: Upsert using a fully fluent style
    var pet1 = wake(PetRow.class).of(ds).upsert(row -> {
      row.setName("Donut");
      row.setOwner("Mark");
      row.setSex("F");
      row.setSpecies("Dog");
      row.setBirth(toUtilDate(2015, 3, 15));
    });
    out.println("  upsert pet #1: " + pet1.getId() + ", " + pet1.getName());

    // style 2: Upsert using style where we hold the repository reference.
    var repo = wake(PetRow.class).of(ds);
    PetRow pet2 = repo.define();
    pet2.setName("Tig");
    pet2.setOwner("None");
    pet2.setSex("M");
    pet2.setSpecies("Dog");
    pet2.setBirth(toUtilDate(2018, 4, 22));
    repo.upsert(pet2);
    out.println("  upsert pet #2: " + pet2.getId() + ", " + pet2.getName());
    Long pet2Id = pet2.getId();

    // Find with ID and update
    var foundPet = repo.id(pet2Id).find();
    out.println("  found: " + foundPet.getName());
    foundPet.setOwner("Mark");
    repo.update(foundPet);

    // Find with a key
    foundPet = repo.key(r -> {
      r.setName("Tig");
      r.setSpecies("Dog");
    }).find();
    out.println("  found: " + foundPet.getName() + ", " + foundPet.getOwner());

    // Delete
    repo.delete(foundPet);

    // Upsert to add row for query
    repo.upsert(row -> {
      row.setName("Twyla");
      row.setOwner("Kendra");
      row.setSex("F");
      row.setSpecies("Cat");
      row.setBirth(toUtilDate(2012, 6, 5));
    });

    // Upsert to add row for query
    repo.upsert(row -> {
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
