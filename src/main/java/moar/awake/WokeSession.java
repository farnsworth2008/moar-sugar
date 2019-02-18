package moar.awake;

import static moar.awake.Waker.runWokeTransaction;
import java.util.function.Consumer;

public abstract class WokeSession
    extends
    WokeSessionBase {
  public void run(Consumer<WokeTxSession> tx) {
    runWokeTransaction(this, tx);
  }
}
