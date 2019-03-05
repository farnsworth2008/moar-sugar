package moar.ansi;

import static java.lang.Math.min;
import static java.lang.String.format;
import static moar.ansi.Ansi.GREEN_BOLD;
import static moar.ansi.Ansi.enabled;
import static moar.ansi.Ansi.purpleBold;
import static moar.sugar.Sugar.bos;
import static moar.sugar.Sugar.require;
import static org.apache.commons.lang3.StringUtils.repeat;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import moar.sugar.CallableVoid;
import moar.sugar.FunctionVoid;

public class StatusLine {

  private final PrintStream out;
  private String label = "";
  private float percentDone;
  private final AtomicInteger count = new AtomicInteger();
  private final AtomicInteger completed = new AtomicInteger();

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

  public void complete(int number) {
    synchronized (this) {
      if (count.get() > 0) {
        percentDone = min(1, (float) completed.addAndGet(number) / count.get());
        render();
      }
    }
  }

  public void completeOne() {
    complete(1);
  }

  public <T> T completeOne(Callable<T> call) throws Exception {
    try {
      return call.call();
    } finally {
      completeOne();
    }
  }

  public void completeOne(CallableVoid call) throws Exception {
    call.call();
    completeOne();
  }

  private void output(ByteArrayOutputStream bos) {
    reset();
    require(() -> out.write(bos.toByteArray()));
    out.println();
    render();
  }

  public <R> R output(Function<PrintStream, R> dispalyUpdater) {
    ByteArrayOutputStream bos = bos();
    try (PrintStream out = new PrintStream(bos)) {
      try {
        return dispalyUpdater.apply(out);
      } finally {
        out.flush();
        output(bos);
      }
    }
  }

  public void output(FunctionVoid<PrintStream> dispalyUpdater) {
    ByteArrayOutputStream bos = bos();
    try (PrintStream out = new PrintStream(bos)) {
      dispalyUpdater.apply(out);
      out.flush();
      output(bos);
    }
  }

  public void render() {
    synchronized (this) {
      reset();
      if (percentDone == 0) {
        out.println(format("%s %s", GREEN_BOLD.apply("Running:"), purpleBold(label)));
      } else {
        String percent = format("%d", (int) (100 * percentDone)) + "%" + " ";
        if (!enabled()) {
          out.println(format("%s %s", percent, label));
          return;
        }
        int size = 20;
        int completed = (int) (size * percentDone);
        int remaining = size - completed;
        String completeBar = repeat("=", completed);
        String remainBar = repeat("-", remaining);
        out.println(purpleBold("<") + GREEN_BOLD.apply(completeBar) + purpleBold(remainBar)
            + purpleBold(">" + " " + percent + label));
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
    label = value;
    render();
  }

  public void setCount(int size) {
    count.set(size);
    completed.set(0);
    percentDone = 0;
  }

  public void setCount(int size, String string) {
    setCount(size);
    set(string);
  }
}
