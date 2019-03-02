package moar.ansi;

import static java.lang.String.format;
import static moar.ansi.Ansi.BLACK_BOLD;
import static moar.ansi.Ansi.GREEN_BOLD;
import static moar.ansi.Ansi.clearLine;
import java.io.PrintStream;

public class ProgressBar {

  private final PrintStream out;
  private final String label;
  private float value;

  public ProgressBar(PrintStream out, String string) {
    this.out = out;
    out.println();
    label = string;
    render();
  }

  public void clear() {
    clearLine(out);
  }

  private void render() {
    if (!Ansi.enabled()) {
      return;
    }
    int size = 20;
    int completed = (int) (size * value);
    int remaining = size - completed;
    clear();
    String percent = format("%d", (int) (100 * value)) + "%" + " ";
    String completeBar = GREEN_BOLD.apply("=".repeat(completed));
    String remainBar = "-".repeat(remaining);
    Ansi bold = BLACK_BOLD;
    out.println(bold.apply("<") + completeBar + bold.apply(remainBar) + bold.apply(">" + " " + percent + label));
  }

  public synchronized void set(float value) {
    if (value != this.value) {
      this.value = value;
      render();
    }
  }

}
