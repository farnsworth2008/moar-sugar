package moar.sugar;

public class ExecuteResult {

  private final Integer exitCode;
  private final String output;

  public ExecuteResult(Integer exitCode, String output) {
    this.exitCode = exitCode;
    this.output = output;
  }

  public Integer getExitCode() {
    return exitCode;
  }

  public String getOutput() {
    return output;
  }

}
