package moar.ansi;

import static java.lang.String.format;
import static moar.ansi.Ansi.GREEN_BOLD;
import static moar.ansi.Ansi.clearLine;
import static moar.ansi.Ansi.purpleBold;
import static org.apache.commons.lang3.StringUtils.repeat;
import java.io.PrintStream;
import java.util.function.Supplier;

public class StatusLine {

  private final PrintStream out;
  private String label;
  private float percentDone;

  public StatusLine(PrintStream out, String string) {
    this.out = out;
    label = string;
    render();
  }

  public void clear() {
    clearLine(out);
  }

  private void render() {
    if (percentDone == 0) {
      out.println(format("%s%s %s", GREEN_BOLD.apply("Running"), purpleBold(":"), purpleBold(label)));
    } else {
      String percent = format("%d", (int) (100 * percentDone)) + "%" + " ";
      if (!Ansi.enabled()) {
        out.println(format("%s %s", percent, label));
        return;
      }
      int size = 20;
      int completed = (int) (size * percentDone);
      int remaining = size - completed;
      clear();
      String completeBar = repeat("=", completed);
      String remainBar = repeat("-", remaining);
      out.println(purpleBold("<") + GREEN_BOLD.apply(completeBar) + purpleBold(remainBar)
          + purpleBold(">" + " " + percent + label));
    }
  }

  public void set(String value) {
    label = value;
  }

  public void set(Supplier<Float> supplier) {
    synchronized (this) {
      Float value = supplier.get();
      if (percentDone != value) {
        percentDone = value;
        render();
      }
    }
  }

}
