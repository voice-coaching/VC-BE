package org.example.voice.analysis.exception;

/** Fixed public codes only; no underlying SQL, private document or process diagnostics. */
public final class CanonicalAnalysisViewException extends RuntimeException {
    public enum Reason {
        INVALID_ANALYSIS_ID(400, "분석 식별자가 올바르지 않습니다."),
        RESOURCE_NOT_FOUND(404, "조회할 수 있는 분석 결과가 없습니다."),
        CANONICAL_ANALYSIS_NOT_FOUND(404, "기존 분석 결과 조회를 이용해 주세요."),
        CANONICAL_ANALYSIS_CHANGED(409, "분석 상태가 변경되었습니다. 다시 조회해 주세요."),
        CANONICAL_RESULT_UNAVAILABLE(503, "분석 결과를 안전하게 확인하지 못했습니다. 다시 조회해 주세요.");

        public final int status;
        public final String message;
        Reason(int status, String message) { this.status=status;this.message=message; }
    }
    private final Reason reason;
    public CanonicalAnalysisViewException(Reason reason) { super(reason.name());this.reason=reason; }
    public Reason reason() { return reason; }
}
