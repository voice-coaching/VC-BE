package org.example.voice.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "잘못된 입력값입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요하거나 Access Token이 만료되었습니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

    // Auth
    INVALID_EMAIL_FORMAT(HttpStatus.BAD_REQUEST, "이메일 형식이 올바르지 않습니다."),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    USER_SUSPENDED(HttpStatus.FORBIDDEN, "이용이 제한된 계정입니다."),
    UNSUPPORTED_SOCIAL_PROVIDER(HttpStatus.BAD_REQUEST, "지원하지 않는 SNS 제공자입니다."),
    INVALID_AUTHORIZATION_CODE(HttpStatus.UNAUTHORIZED, "SNS 인증 정보가 유효하지 않습니다."),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "세션이 만료되었습니다. 다시 로그인해 주세요."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 Refresh Token입니다."),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    NICKNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),

    WITHDRAWAL_ALREADY_PROCESSED(HttpStatus.CONFLICT, "이미 탈퇴 처리된 사용자입니다."),

    // Onboarding
    REQUIRED_ANSWER_MISSING(HttpStatus.BAD_REQUEST, "필수 설문 항목이 누락되었습니다."),
    ONBOARDING_NOT_FOUND(HttpStatus.NOT_FOUND, "온보딩 정보를 찾을 수 없습니다."),

    // Home
    RECENT_TRAINING_NOT_FOUND(HttpStatus.NOT_FOUND, "이어할 학습 기록이 없습니다."),

    // Practice Content
    CONTENT_NOT_FOUND(HttpStatus.NOT_FOUND, "학습 콘텐츠를 찾을 수 없습니다."),
    REFERENCE_AUDIO_NOT_FOUND(HttpStatus.NOT_FOUND, "기준 음성을 찾을 수 없습니다."),
    NEXT_CONTENT_NOT_FOUND(HttpStatus.NOT_FOUND, "조건에 맞는 다음 콘텐츠가 없습니다."),

    // Training
    CONTENT_NOT_AVAILABLE(HttpStatus.CONFLICT, "현재 학습할 수 없는 콘텐츠입니다."),
    TRAINING_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "학습 세션을 찾을 수 없습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    UNSUPPORTED_AUDIO_FORMAT(HttpStatus.BAD_REQUEST, "지원하지 않는 녹음 파일 형식입니다."),
    AUDIO_FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "업로드 가능한 녹음 파일 크기를 초과했습니다."),
    VIDEO_PROCESSING_CONSENT_REQUIRED(HttpStatus.BAD_REQUEST, "얼굴 영상 처리에 대한 명시적 동의가 필요합니다."),
    UPLOADED_OBJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "업로드된 음성 파일을 찾을 수 없습니다."),
    UPLOAD_INTENT_NOT_FOUND(HttpStatus.CONFLICT, "백엔드가 발급한 유효한 업로드 요청을 찾을 수 없습니다."),
    UPLOAD_INTENT_NOT_ACTIVE(HttpStatus.CONFLICT, "이미 완료되었거나 만료된 업로드 요청입니다."),
    RECORDING_ALREADY_REGISTERED(HttpStatus.CONFLICT, "이미 등록된 음성 파일입니다."),
    RECORDING_NOT_FOUND(HttpStatus.NOT_FOUND, "녹음 파일을 찾을 수 없습니다."),
    RECORDING_QUALITY_FAILED(HttpStatus.CONFLICT, "음질 검사를 통과하지 못한 녹음입니다."),
    SELECTED_RECORDING_CANNOT_DELETE(HttpStatus.CONFLICT, "최종 선택된 녹음은 먼저 선택을 해제해야 합니다."),
    ANALYZED_RECORDING_CANNOT_DELETE(HttpStatus.CONFLICT, "분석 완료된 녹음은 학습 기록 삭제 API를 이용해 주세요."),
    SELECTED_RECORDING_NOT_FOUND(HttpStatus.CONFLICT, "분석할 최종 녹음을 선택해 주세요."),
    AUDIO_QUALITY_NOT_ACCEPTABLE(HttpStatus.UNPROCESSABLE_ENTITY, "음질이 낮아 분석할 수 없습니다. 다시 녹음해 주세요."),
    ANALYSIS_SOURCE_NOT_READY(HttpStatus.UNPROCESSABLE_ENTITY, "분석에 사용할 녹음 정보를 안전하게 확인할 수 없습니다. 다시 녹음해 주세요."),
    MEDIA_NORMALIZATION_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "지원되는 형식으로 녹음을 처리할 수 없습니다. 다시 녹음해 주세요."),
    ANALYSIS_CONSENT_REQUIRED(HttpStatus.BAD_REQUEST, "음성 분석을 위한 명시적 동의가 필요합니다."),
    ANALYSIS_CONSENT_POLICY_MISMATCH(HttpStatus.CONFLICT, "현재 동의 정책을 다시 확인해 주세요."),
    ANALYSIS_ALREADY_RUNNING(HttpStatus.CONFLICT, "이미 분석이 진행 중입니다."),
    ANALYSIS_ALREADY_REQUESTED(HttpStatus.CONFLICT, "이미 분석 요청 이력이 있습니다. 실패한 요청은 재시도 API를 이용해 주세요."),
    ANALYSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "분석 요청 기록이 없습니다."),
    ANALYSIS_NOT_FAILED(HttpStatus.CONFLICT, "실패한 분석만 재시도할 수 있습니다."),
    MAX_RETRY_EXCEEDED(HttpStatus.CONFLICT, "분석 재시도 가능 횟수를 초과했습니다."),
    ANALYSIS_INTEGRATION_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "현재 분석 서비스를 사용할 수 없습니다. 잠시 후 다시 시도해 주세요."),
    ANALYSIS_NOT_COMPLETED(HttpStatus.CONFLICT, "분석이 완료된 후 학습을 종료할 수 있습니다."),
    INVALID_SESSION_STATE(HttpStatus.CONFLICT, "현재 학습 세션 상태에서는 요청한 작업을 수행할 수 없습니다."),
    SESSION_ALREADY_FINISHED(HttpStatus.CONFLICT, "이미 종료된 학습 세션입니다."),
    RECORDING_ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 녹음에 접근할 권한이 없습니다."),
    TRAINING_SESSION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 학습 기록에 접근할 권한이 없습니다."),

    // Analysis Result
    SESSION_ANALYSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "학습 세션의 분석 결과가 없습니다."),
    FEEDBACK_REGENERATION_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "피드백 재생성 가능 횟수를 초과했습니다."),

    // Course
    COURSE_NOT_FOUND(HttpStatus.NOT_FOUND, "클래스를 찾을 수 없습니다."),
    COURSE_ALREADY_COMPLETED(HttpStatus.CONFLICT, "이미 완료한 클래스입니다."),
    COURSE_PROGRESS_NOT_FOUND(HttpStatus.NOT_FOUND, "클래스 시작 기록이 없습니다."),
    INVALID_PROGRESS_PERCENT(HttpStatus.BAD_REQUEST, "진행률은 0에서 100 사이여야 합니다."),
    INVALID_COURSE_STEP(HttpStatus.CONFLICT, "해당 클래스에 포함되지 않은 단계입니다."),
    REQUIRED_STEP_NOT_COMPLETED(HttpStatus.CONFLICT, "완료하지 않은 필수 단계가 있습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
