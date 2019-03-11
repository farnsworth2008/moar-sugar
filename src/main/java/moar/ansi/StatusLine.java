package moar.ansi;

import static java.lang.Math.min;
import static java.lang.String.format;
import static moar.ansi.Ansi.GREEN_BOLD;
import static moar.ansi.Ansi.cyanBold;
import static moar.ansi.Ansi.enabled;
import static moar.ansi.Ansi.greenBold;
import static moar.ansi.Ansi.purpleBold;
import static moar.sugar.Sugar.bos;
import static moar.sugar.Sugar.require;
import static org.apache.commons.lang3.StringUtils.repeat;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import com.google.common.util.concurrent.AtomicDouble;
import moar.sugar.FunctionVoid;

public class StatusLine {

  private final PrintStream out;
  private final AtomicReference<String> label = new AtomicReference<String>("");
  private final AtomicDouble percentDone = new AtomicDouble();
  private final AtomicLong count = new AtomicLong();
  private final AtomicLong completed = new AtomicLong();

  public StatusLine() {
    this(System.out);
  }

  public StatusLine(PrintStream out) {
    this.out = out;
    out.println();
    render();
  }

  public void clear() {
    synchronized (this) {
      if (enabled()) {
        reset();
        out.flush();
      }
    }
  }

  public void complete(long number) {
    synchronized (this) {
      if (count.get() > 0) {
        percentDone.set(min(1, (double) completed.addAndGet(number) / count.get()));
        render();
      }
    }
  }

  public void completeOne() {
    complete(1);
  }

  private void output(ByteArrayOutputStream bos) {
    reset();
    require(() -> out.write(bos.toByteArray()));
    out.println();
    render();
  }

  public void output(FunctionVoid<PrintStream> out) {
    synchronized (this) {
      ByteArrayOutputStream bos = bos();
      try (PrintStream os = new PrintStream(bos)) {
        out.apply(os);
        os.flush();
        output(bos);
      }
    }
  }

  public void render() {
    synchronized (this) {
      reset();
      if (percentDone.get() == 0 && count.get() == 0) {
        out.println(format("%s %s", GREEN_BOLD.apply("Running:"), purpleBold(label.get())));
      } else {
        String percent = format("%d", (int) (100 * percentDone.get())) + "%" + " ";
        if (!enabled()) {
          out.println(format("%s %s", percent, label.get()));
          return;
        }
        int size = 20;
        int completed = (int) (size * percentDone.get());
        int remaining = size - completed;
        String completeBar = repeat("=", completed);
        String remainBar = repeat("-", remaining);
        out.println(cyanBold("<") + greenBold(completeBar) + purpleBold(remainBar) + cyanBold(">") + " "
            + greenBold(percent) + purpleBold(label));
      }
      out.flush();
    }
  }

  private void reset() {
    if (enabled()) {
      synchronized (this) {
        out.flush();
        out.print("\033[1A");
        out.print("\033[2K");
        out.flush();
      }
    }
  }

  public void set(String value) {
    label.set(value);
    render();
  }

  public void setCount(long value) {
    count.set(value);
    completed.set(0);
    percentDone.set(0);
  }

  public void setCount(long count, String string) {
    setCount(count);
    set(string);
  }
}
