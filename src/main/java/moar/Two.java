package moar;

public class Two<T1, T2> {
  private T1 one;
  private T2 two;

  public Two(final T1 one, final T2 two) {
    this.one = one;
    this.two = two;
  }

  public T1 getOne() {
    return one;
  }

  public T2 getTwo() {
    return two;
  }

  public void setOne(final T1 value) {
    one = value;
  }

  public void setTwo(final T2 value) {
    two = value;
  }
}
