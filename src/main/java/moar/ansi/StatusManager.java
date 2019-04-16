package moar.ansi;

public interface StatusManager {

  void complete(long number);

  void set(String string);

  void setCount(long number, String text);

}
