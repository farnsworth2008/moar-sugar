package moar;

import java.util.List;

public class FutureListException
    extends
    RuntimeException {

  private final List<Two<Object, Exception>> results;

  public FutureListException(final List<Two<Object, Exception>> results) {
    this.results = results;
  }

  public List<Two<Object, Exception>> getResults() {
    return results;
  }

}
