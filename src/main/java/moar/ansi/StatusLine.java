package moar.ansi;

import static java.lang.String.format;
import static moar.ansi.Ansi.GREEN_BOLD;
import static moar.ansi.Ansi.bold;
import static moar.ansi.Ansi.clearLine;
import static org.apache.commons.lang3.StringUtils.repeat;
import java.io.PrintStream;
import java.util.function.Supplier;

public class StatusLine {

  private final PrintStream out;
  private final String label;
  private float value;

  public StatusLine(PrintStream out, String string) {
    this.out = out;
    label = string;
    render();
  }

  public void clear() {
    clearLine(out);
  }

  private void render() {
    if (value == 0) {
      out.println(format("%s%s %s", GREEN_BOLD.apply("Running"), bold(":"), bold(label)));
    } else {
      String percent = format("%d", (int) (100 * value)) + "%" + " ";
      if (!Ansi.enabled()) {
        out.println(format("%s %s", percent, label));
        return;
      }
      int size = 20;
      int completed = (int) (size * value);
      int remaining = size - completed;
      clear();
      String completeBar = repeat("=", completed);
      String remainBar = repeat("-", remaining);
      out.println(bold("<") + GREEN_BOLD.apply(completeBar) + bold(remainBar) + bold(">" + " " + percent + label));
    }
  }

  public void set(Supplier<Float> percentDone) {
    synchronized (this) {
      Float newValue = percentDone.get();
      if (value != newValue) {
        value = newValue;
        render();
      }
    }
  }

}
