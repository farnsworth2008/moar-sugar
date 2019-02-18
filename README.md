# Moar Sugar

Stuff to make Java sweet!

## Example #1, async methods

```java
  void exampleAsyncSugar(PrintStream out) {
    out.println("  ASYNC METHODS WITH MOAR SUGAR");
    AsyncService service = $(4);
    try {
      Vector<Future<String>> futures = $(String.class);
      for (int i = 0; i < 3; i++) {
        String message = "async " + i;
        $(service, futures, () -> methodOne(out, message));
      }
      for (int i = 0; i < 3; i++) {
        String message = "async " + i;
        $(service, futures, () -> methodTwo(out, message));
      }
      for (int i = 0; i < 3; i++) {
        String message = "async " + i;
        $(service, futures, () -> methodThatThrows(out, message));
      }
      out.println("  async work started");
      swallow(() -> $(futures));
      out.println("  async work complete");

      int i = 0;
      for (Future<String> future : futures) {
        SafeResult<String> result = safely(() -> future.get());
        String displayValue = result.thrown() == null ? result.get() : result.thrown().getMessage();
        out.println(format("  futures ++i, displayValue));
      }
    } finally {
      service.shutdown();
    }
    out.println();
  }
```

## Example #2, database CRUD

```java
  void exampleDb(PrintStream out) {
    out.println("Example: DB");

    wake(ds).executeSql("delete from pet");

    //style 1: Upsert using a fully fluent style
    PetRow pet1 = wake(PetRow.class).of(ds).upsert(row -> {
      row.setName("Donut");
      row.setOwner("Mark");
      row.setSex("F");
      row.setSpecies("Dog");
      row.setBirth(toUtilDate(2015, 3, 15));
    });
    out.println("  upsert pet #1: " + pet1.getId() + ", " + pet1.getName());

    //style 2: Upsert using style where we hold the repository reference.
    WokenWithSession<PetRow> repo = wake(PetRow.class).of(ds);
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
    PetRow foundPet = repo.id(pet2Id).find();
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

    repo.upsert(row -> {
      row.setName("Twyla");
      row.setOwner("Kendra");
      row.setSex("F");
      row.setSpecies("Cat");
      row.setBirth(toUtilDate(2012, 6, 5));
    });

    repo.upsert(row -> {
      row.setName("Jasper");
      row.setOwner("Kendra");
      row.setSex("M");
      row.setSpecies("Cat");
      row.setBirth(toUtilDate(2012, 9, 1));
    });

    // Find with a query
    List<PetRow> petList
        = repo.list("select [*] from pet as PetRow where species=?", "Cat");
    for (PetRow petItem : petList) {
      out.println("  found: " + petItem.getName() + ", " + petItem.getOwner());
    }
  }
```

Additional Examples:
----------

See the [Moar Sugar Example App](https://github.com/farnsworth2008/moar-sugar-example/blob/master/moar-sugar-app/src/main/java/moar/sugar/example/MoarSugarExampleApp.java) for additional examples.
