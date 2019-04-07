package moar.ansi;

import static java.lang.Math.min;
import static java.lang.String.format;
import static moar.ansi.Ansi.GREEN_BOLD;
import static moar.ansi.Ansi.cyanBold;
import static moar.ansi.Ansi.enabled;
import static moar.ansi.Ansi.greenBold;
import static moar.ansi.Ansi.purpleBold;
import static moar.ansi.Ansi.upOneLine;
import static moar.sugar.Sugar.bos;
import static org.apache.commons.lang3.StringUtils.repeat;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import com.google.common.util.concurrent.AtomicDouble;

public class StatusLine
    implements
    StatusManager {

  private static StatusLine current;
  private final AtomicReference<String> label = new AtomicReference<>("");
  private final AtomicDouble percentDone = new AtomicDouble();
  private final AtomicLong count = new AtomicLong();
  private final AtomicLong completed = new AtomicLong();
  private final PrintStream out;
  private final PrintStream outBuffer;
  private final ByteArrayOutputStream outBufferBytes;
  private final StatusLine parent;
  private String lastLine;

  public StatusLine() {
    this(current);
  }

  public StatusLine(long count, String string) {
    this();
    setCount(count, string);
  }

  private StatusLine(StatusLine parent) {
    if (parent != null) {
      parent.release(false);
    }
    this.parent = parent;
    current = this;
    out = System.out;
    outBufferBytes = bos();
    outBuffer = new PrintStream(outBufferBytes);
    if (enabled()) {
      capture();
    }
  }

  public StatusLine(String string) {
    this();
    set(string);
  }

  private void capture() {
    System.setOut(outBuffer);
    out.println();
    render();
  }

  @Override
  public synchronized void complete(long number) {
    if (count.get() > 0) {
      percentDone.set(min(1, (double) completed.addAndGet(number) / count.get()));
      render();
    }
  }

  public void completeOne() {
    complete(1);
  }

  public long getCompleted() {
    return completed.get();
  }

  public void output(Runnable call) {
    release(true);
    call.run();
    capture();
  }

  private void release(boolean reset) {
    render();
    if (reset) {
      reset();
    }
    out.flush();
    System.setOut(out);
  }

  public void remove() {
    if (enabled()) {
      release(true);
      if (parent != null) {
        out.flush();
        out.print(upOneLine());
        out.print("\033[2K");
        out.flush();
        parent.capture();
      }
      current = parent;
    }
  }

  public void render() {
    reset();
    byte[] bytes = outBufferBytes.toByteArray();
    if (bytes.length > 0) {
      out.print(new String(bytes));
      outBufferBytes.reset();
    }
    if (percentDone.get() == 0 && count.get() == 0) {
      out.println(format("%s %s", GREEN_BOLD.apply("Running:"), purpleBold(label.get())));
      out.flush();
    } else {
      String percent = format("%d", (int) (100 * percentDone.get())) + "%" + " ";
      String line;
      if (!enabled()) {
        line = format("%s %s", percent, label.get());
        if (!line.equals(lastLine)) {
          out.println(line);
          out.flush();
        }
      } else {
        int size = 20;
        int completed = (int) (size * percentDone.get());
        int remaining = size - completed;
        String completeBar = repeat("=", completed);
        String remainBar = repeat("-", remaining);
        line = cyanBold("<") + greenBold(completeBar) + purpleBold(remainBar) + cyanBold(">") + " " + greenBold(percent)
            + purpleBold(label);
        out.println(line);
        out.flush();
      }
      lastLine = line;
    }
  }

  private void reset() {
    if (enabled()) {
      out.flush();
      out.print(upOneLine());
      out.print("\033[2K");
      out.flush();
    }
  }

  @Override
  public synchronized void set(String value) {
    label.set(value);
    render();
  }

  public void setCount(long value) {
    count.set(value);
    completed.set(0);
    percentDone.set(0);
  }

  @Override
  public void setCount(long count, String string) {
    setCount(count);
    set(string);
  }
}
