package moar.driver;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

class StatementReader {
  private final StringBuilder text = new StringBuilder();
  private final InputStream stream;

  StatementReader(InputStream s) throws IOException {
    ByteArrayOutputStream bo = new ByteArrayOutputStream();
    try (InputStreamReader r = new InputStreamReader(s)) {
      BufferedReader br = new BufferedReader(r);
      boolean delimiterMode = false;
      String line;
      while (null != (line = br.readLine())) {
        line = line.trim();
        if (delimiterMode) {
          line = line.replaceAll(";", "%3B");
          if (line.endsWith("$$")) {
            line = line.substring(0, line.length() - "$$".length()) + ";";
          }
        }
        if (line.startsWith("DELIMITER $$")) {
          delimiterMode = true;
        } else if (!line.startsWith("--")) {
          line += "\n";
          bo.write(line.getBytes());
        }
      }
    }
    byte[] b = bo.toByteArray();
    stream = new ByteArrayInputStream(b);
  }

  private void readQuotedString(char quoteType) throws IOException {
    int c;
    while (-1 != (c = stream.read())) {
      char ch = (char) c;
      text.append(ch);
      if (ch == '\\') {
        c = stream.read();
        ch = (char) c;
        switch (ch) {
          case 'n':
            text.append("\n");
            break;
          case 'r':
            text.append("\r");
            break;
          case '\\':
            text.append("\\");
            break;
          case '\'':
            text.append("\'");
            break;
          case '\"':
            text.append("\"");
            break;
          case '\t':
            text.append("\t");
            break;
          default:
            throw new RuntimeException("Unexpected escape");
        }
      } else if (ch == quoteType) {
        return;
      }
    }
    throw new RuntimeException("Expected closing quote of type (" + (int) quoteType + ")");
  }

  String readStatement() throws IOException {
    text.setLength(0);
    int c;
    char quote = '\'';
    char doubleQuote = '"';
    while (-1 != (c = stream.read())) {
      char ch = (char) c;
      if (ch == ';') {
        break;
      } else if (ch == quote) {
        text.append(ch);
        readQuotedString(quote);
      } else if (ch == doubleQuote) {
        text.append(ch);
        readQuotedString(doubleQuote);
      } else if (ch == '\t') {
        text.append(' ');
      } else if (ch == '\n') {
        text.append(' ');
      } else if (ch == '\r') {
        text.append(' ');
      } else {
        text.append(ch);
      }
    }
    String trim = text.toString().trim();
    return trim.equals("") ? null : trim;
  }
}
