package moar.ansi;

import static java.lang.String.format;
import static moar.ansi.Ansi.*;
import static moar.sugar.Sugar.swallow;
import java.io.PrintStream;

public class ProgressBar {

  private final PrintStream out;
  private final String label;
  private float value;

  public ProgressBar(PrintStream out, String string) {
    this.out = out;
    out.println();
    this.label = string;
    render();
  }

  public synchronized void set(float value) {
    if (value != this.value) {
      this.value = value;
      render();
    }
  }

  private void render() {
    int size = 20;
    int completed = (int) (size * this.value);
    int remaining = size - completed;
    clear();
    String percent = format("%d", (int) (100 * this.value)) + "%" + " ";
    String completeBar = GREEN_BOLD.apply("=".repeat(completed));
    String remainBar = "-".repeat(remaining);
    Ansi bold = BLACK_BOLD;
    out.println(bold.apply("<") + completeBar + bold.apply(remainBar) + bold.apply(">" + " " + percent + label));
  }

  public void clear() {
    clearLine(out);
  }

}
