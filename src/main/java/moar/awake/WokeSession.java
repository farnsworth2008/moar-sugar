package moar.awake;

import static moar.awake.WokeRepository.runWokeTransaction;
import java.util.function.Consumer;

/**
 * A session for running Woke transactions.
 *
 * @author Mark Farnsworth
 */
public abstract class WokeSession
    extends
    WokeSessionBase {

  /**
   * @param tx
   *   Transaction(s) to run.
   */
  @SafeVarargs
  public final void run(Consumer<WokeTxSession>... tx) {
    for (Consumer<WokeTxSession> t : tx) {
      runWokeTransaction(this, t);
    }
  }

}
