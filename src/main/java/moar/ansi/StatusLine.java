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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import moar.sugar.CallableVoid;
import moar.sugar.FunctionVoid;

public class StatusLine {

  private final PrintStream out;
  private String label = "";
  private float percentDone;
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
        percentDone = min(1, (float) completed.addAndGet(number) / count.get());
        render();
      }
    }
  }

  public void completeOne() {
    complete(1);
  }

  public <T> T completeOne(Callable<T> call) {
    try {
      return require(() -> call.call());
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

  public <R> R output(Function<PrintStream, R> out) {
    ByteArrayOutputStream bos = bos();
    try (PrintStream os = new PrintStream(bos)) {
      try {
        return out.apply(os);
      } finally {
        os.flush();
        output(bos);
      }
    }
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

  public void setCount(long count) {
    this.count.set(count);
    completed.set(0);
    percentDone = 0;
  }

  public void setCount(long count, String string) {
    setCount(count);
    set(string);
  }
}
