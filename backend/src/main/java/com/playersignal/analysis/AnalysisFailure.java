package com.playersignal.analysis;

/** Only sanitized, actionable messages belong here; never raw provider bodies or keys. */
public class AnalysisFailure extends RuntimeException {
    public final String code;
    public final boolean retryable;
    public final boolean stopRun;
    public AnalysisFailure(String code, String message, boolean retryable, boolean stopRun) {
        super(message); this.code = code; this.retryable = retryable; this.stopRun = stopRun;
    }
    public static AnalysisFailure invalid() {
        return new AnalysisFailure("INVALID_OUTPUT", "The analyzer returned invalid classification or unsupported evidence.", true, false);
    }
}
