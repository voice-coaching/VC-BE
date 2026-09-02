package org.example.voice.common.config;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class OpenApiKoreanDocumentation {
    private static final Map<ApiKey, ApiDoc> DOCS = docs();

    @Bean
    OpenApiCustomizer koreanApiDocumentationCustomizer() {
        return openApi -> {
            openApi.setTags(List.of(
                    tag("인증", "회원가입, 로그인, SNS 로그인과 토큰 수명 주기를 관리합니다."),
                    tag("사용자", "내 프로필, 회원 탈퇴와 최근 학습 정보를 관리합니다."),
                    tag("온보딩", "사용자의 학습 수준, 목표와 설문 답변을 저장하고 조회합니다."),
                    tag("홈", "홈 화면의 학습 현황과 맞춤 추천 데이터를 제공합니다."),
                    tag("학습 콘텐츠", "연습 콘텐츠와 예시 음성 정보를 조회합니다."),
                    tag("학습 세션", "학습 세션, 녹음 파일과 분석 요청 과정을 관리합니다."),
                    tag("분석 결과", "완료된 음성 분석 점수, 구간별 피드백과 종합 피드백을 제공합니다."),
                    tag("클래스", "클래스 목록, 단계와 사용자별 학습 진도를 관리합니다."),
                    tag("마이페이지", "내 학습 기록, 통계, 강점·약점과 맞춤 추천을 제공합니다.")
            ));
            DOCS.forEach((key, doc) -> {
                PathItem pathItem = openApi.getPaths().get(key.path());
                if (pathItem == null) return;
                Operation operation = pathItem.readOperationsMap().get(key.method());
                if (operation == null) return;
                operation.setTags(List.of(doc.tag()));
                operation.setSummary(doc.summary());
                operation.setDescription(doc.description());
                if (doc.publicApi()) operation.setSecurity(List.of());
                else operation.setSecurity(List.of(new SecurityRequirement().addList(OpenApiConfig.BEARER_AUTH)));
                describeParameters(operation);
                if (operation.getResponses() != null && operation.getResponses().get("200") != null) {
                    operation.getResponses().get("200").setDescription("요청을 정상적으로 처리했습니다.");
                }
            });
            describeSchemas(openApi);
        };
    }

    static Map<ApiKey, ApiDoc> documentation() { return DOCS; }

    private static void describeParameters(Operation operation) {
        if (operation.getParameters() == null) return;
        operation.getParameters().forEach(parameter -> {
            String name = parameter.getName();
            parameter.setDescription(switch (name) {
                case "email" -> "중복 여부를 확인할 이메일 주소";
                case "sessionId" -> "학습 세션 ID";
                case "recordingId" -> "녹음 파일 ID";
                case "analysisId" -> "음성 분석 결과 ID";
                case "contentId" -> "학습 콘텐츠 ID";
                case "audioId" -> "예시 음성 ID";
                case "courseId" -> "클래스 ID";
                case "page" -> "조회할 페이지 번호이며 0부터 시작합니다.";
                case "size" -> "한 페이지에 조회할 항목 수";
                case "refreshToken" -> "HttpOnly 쿠키로 전달되는 Refresh Token";
                case "period" -> "조회 기간(WEEK, MONTH, THREE_MONTHS, YEAR)";
                case "from" -> "조회 시작일(yyyy-MM-dd)";
                case "to" -> "조회 종료일(yyyy-MM-dd)";
                case "metric" -> "점수 지표(OVERALL, PRONUNCIATION, INTONATION)";
                case "contentType" -> "추천 콘텐츠 유형";
                default -> parameter.getDescription();
            });
        });
    }

    private static void describeSchemas(io.swagger.v3.oas.models.OpenAPI openApi) {
        if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) return;
        Map<String, String> descriptions = Map.ofEntries(
                Map.entry("email", "사용자 이메일 주소"), Map.entry("password", "영문, 숫자, 특수문자를 포함한 비밀번호"),
                Map.entry("nickname", "서비스에서 표시할 사용자 닉네임"), Map.entry("termsAgreed", "서비스 이용약관 필수 동의 여부"),
                Map.entry("privacyAgreed", "개인정보 처리방침 필수 동의 여부"), Map.entry("provider", "SNS 로그인 공급자(GOOGLE 또는 KAKAO)"),
                Map.entry("authorizationCode", "SNS 공급자가 발급한 일회성 인가 코드"), Map.entry("redirectUri", "인가 코드 발급에 사용한 프론트엔드 Redirect URI"),
                Map.entry("currentLevel", "사용자가 선택한 현재 학습 수준"), Map.entry("goalText", "사용자의 학습 목표 문구"),
                Map.entry("dailyGoalMinutes", "하루 목표 학습 시간(분)"), Map.entry("weeklyGoalCount", "주간 목표 학습 횟수"),
                Map.entry("surveyAnswers", "학습 목적과 개선 영역에 대한 설문 답변"), Map.entry("learningPurposes", "학습 목적 목록"),
                Map.entry("improvementAreas", "개선하고 싶은 영역 목록"), Map.entry("pronunciationConcerns", "발음 고민 목록"),
                Map.entry("learningSituations", "주로 학습하려는 상황 목록"), Map.entry("contentId", "학습 콘텐츠 ID"),
                Map.entry("courseStepId", "클래스 단계 ID. 자유 학습이면 생략할 수 있습니다."), Map.entry("learningFocus", "이번 학습에서 집중할 영역"),
                Map.entry("totalLearningSeconds", "실제 학습한 총 시간(초)"), Map.entry("fileName", "업로드할 원본 음성 파일명"),
                Map.entry("mimeType", "음성 파일 MIME 타입"), Map.entry("fileSizeBytes", "음성 파일 크기(byte)"),
                Map.entry("objectKey", "업로드 URL 발급 시 받은 스토리지 객체 키"), Map.entry("durationMs", "녹음 재생 시간(ms)"),
                Map.entry("accepted", "현재 정책에 따른 음성 분석 처리 명시적 동의 여부"), Map.entry("policyRevision", "화면에 표시한 음성 분석 동의 정책 revision"),
                Map.entry("feedbackStyle", "재생성할 피드백 스타일"), Map.entry("lastStepId", "마지막으로 완료한 클래스 단계 ID"),
                Map.entry("progressPercent", "클래스 전체 진행률(0~100)"), Map.entry("type", "조회할 콘텐츠 또는 클래스 유형"),
                Map.entry("difficulty", "학습 난이도"), Map.entry("status", "조회할 상태"), Map.entry("category", "콘텐츠 카테고리"),
                Map.entry("focus", "학습 집중 영역"), Map.entry("limit", "반환할 최대 추천 개수"), Map.entry("excludeId", "다음 콘텐츠 조회에서 제외할 현재 콘텐츠 ID")
        );
        Map<String, Object> examples = Map.ofEntries(
                Map.entry("email", "user@example.com"), Map.entry("nickname", "또박이"), Map.entry("provider", "GOOGLE"),
                Map.entry("authorizationCode", "provider-authorization-code"), Map.entry("redirectUri", "https://vc-fe.vercel.app/oauth/callback"),
                Map.entry("goalText", "뉴스를 또박또박 읽고 싶어요"), Map.entry("dailyGoalMinutes", 20), Map.entry("weeklyGoalCount", 5),
                Map.entry("contentId", 1), Map.entry("courseStepId", 3), Map.entry("totalLearningSeconds", 180),
                Map.entry("fileName", "recording.webm"), Map.entry("mimeType", "audio/webm"), Map.entry("fileSizeBytes", 245760),
                Map.entry("objectKey", "recordings/1/sample.webm"), Map.entry("durationMs", 15000), Map.entry("feedbackStyle", "COACHING"),
                Map.entry("accepted", true), Map.entry("policyRevision", "voice-analysis-consent-v1"),
                Map.entry("progressPercent", 50.0), Map.entry("page", 0), Map.entry("size", 20), Map.entry("limit", 5)
        );
        openApi.getComponents().getSchemas().values().forEach(schema -> {
            if (schema.getProperties() == null) return;
            schema.getProperties().forEach((name, rawProperty) -> {
                io.swagger.v3.oas.models.media.Schema<?> property = (io.swagger.v3.oas.models.media.Schema<?>) rawProperty;
                if (descriptions.containsKey(name)) property.setDescription(descriptions.get(name));
                if (examples.containsKey(name)) property.setExample(examples.get(name));
            });
        });
    }

    private static Tag tag(String name, String description) { return new Tag().name(name).description(description); }
    private static void add(Map<ApiKey, ApiDoc> map, PathItem.HttpMethod method, String path, String tag, String summary, String description, boolean publicApi) {
        map.put(new ApiKey(method, path), new ApiDoc(tag, summary, description, publicApi));
    }

    private static Map<ApiKey, ApiDoc> docs() {
        Map<ApiKey, ApiDoc> map = new LinkedHashMap<>();
        add(map, PathItem.HttpMethod.GET, "/api/auth/email-availability", "인증", "이메일 사용 가능 여부 확인", "회원가입 전에 이메일 형식과 중복 여부를 확인합니다. 중복 이메일은 오류가 아니라 available=false로 반환합니다.", true);
        add(map, PathItem.HttpMethod.POST, "/api/auth/signup", "인증", "일반 회원가입", "이메일, 비밀번호, 닉네임과 필수 약관 동의를 검증해 계정을 만들고 Access Token과 Refresh Token 쿠키를 발급합니다.", true);
        add(map, PathItem.HttpMethod.POST, "/api/auth/login", "인증", "이메일 로그인", "이메일과 비밀번호를 검증하고 기존 로그인 세션을 만료시킨 뒤 새 토큰을 발급합니다.", true);
        add(map, PathItem.HttpMethod.POST, "/api/auth/social-login", "인증", "SNS 로그인", "Google 또는 Kakao 인가 코드를 공급자 토큰으로 교환해 로그인하며, 최초 로그인이라면 회원 계정을 자동 생성합니다.", true);
        add(map, PathItem.HttpMethod.POST, "/api/auth/token/refresh", "인증", "Access Token 갱신", "HttpOnly Refresh Token 쿠키를 검증하고 토큰을 회전해 새로운 Access Token과 Refresh Token 쿠키를 발급합니다.", true);
        add(map, PathItem.HttpMethod.POST, "/api/auth/logout", "인증", "로그아웃", "현재 사용자의 Refresh Token 세션을 폐기하고 브라우저의 Refresh Token 쿠키를 만료시킵니다.", false);

        add(map, PathItem.HttpMethod.GET, "/api/users/me", "사용자", "내 프로필 조회", "인증된 사용자의 이메일, 닉네임, 가입 방식과 온보딩 완료 여부를 조회합니다.", false);
        add(map, PathItem.HttpMethod.PATCH, "/api/users/me", "사용자", "내 프로필 수정", "인증된 사용자의 닉네임을 검증한 뒤 변경합니다.", false);
        add(map, PathItem.HttpMethod.DELETE, "/api/users/me", "사용자", "회원 탈퇴", "사용자 계정을 탈퇴 처리하고 활성 로그인 세션을 사용할 수 없게 만듭니다.", false);
        add(map, PathItem.HttpMethod.GET, "/api/users/me/training-sessions/recent", "사용자", "최근 학습 세션 조회", "내가 가장 최근에 진행한 학습 세션과 콘텐츠 정보를 조회합니다.", false);

        add(map, PathItem.HttpMethod.PUT, "/api/onboarding/me", "온보딩", "온보딩 정보 전체 저장", "현재 수준, 학습 목표, 일간·주간 목표와 설문 답변을 한 번에 저장합니다.", false);
        add(map, PathItem.HttpMethod.GET, "/api/onboarding/me", "온보딩", "내 온보딩 정보 조회", "인증된 사용자가 저장한 학습 목표와 설문 답변 전체를 조회합니다.", false);
        add(map, PathItem.HttpMethod.PATCH, "/api/onboarding/me", "온보딩", "온보딩 정보 일부 수정", "전달한 필드만 선택적으로 변경하며 생략한 값은 기존 값을 유지합니다.", false);

        add(map, PathItem.HttpMethod.GET, "/api/home", "홈", "홈 대시보드 조회", "오늘의 학습 현황, 최근 학습, 클래스 진행률과 추천 콘텐츠를 한 번에 조회합니다.", false);
        add(map, PathItem.HttpMethod.GET, "/api/recommendations", "홈", "맞춤 학습 추천 조회", "사용자의 학습 이력과 요청 조건을 기준으로 추천 콘텐츠 목록을 조회합니다.", false);

        add(map, PathItem.HttpMethod.GET, "/api/practice-contents", "학습 콘텐츠", "학습 콘텐츠 목록 조회", "콘텐츠 유형, 난이도와 검색 조건을 적용해 연습 콘텐츠를 페이지 단위로 조회합니다.", false);
        add(map, PathItem.HttpMethod.GET, "/api/practice-contents/next", "학습 콘텐츠", "다음 학습 콘텐츠 추천", "현재 콘텐츠와 조회 조건을 기준으로 이어서 학습할 다음 콘텐츠를 찾습니다.", false);
        add(map, PathItem.HttpMethod.GET, "/api/practice-contents/{contentId}", "학습 콘텐츠", "학습 콘텐츠 상세 조회", "선택한 콘텐츠의 원문, 난이도, 학습 포인트와 부가 정보를 조회합니다.", false);
        add(map, PathItem.HttpMethod.GET, "/api/practice-contents/{contentId}/reference-audios", "학습 콘텐츠", "예시 음성 목록 조회", "학습 콘텐츠에 등록된 아나운서 또는 예시 발화 음성 목록을 조회합니다.", false);
        add(map, PathItem.HttpMethod.GET, "/api/practice-contents/{contentId}/recommendations", "학습 콘텐츠", "콘텐츠 기반 추천 조회", "선택한 콘텐츠와 난이도 및 학습 초점이 유사한 콘텐츠 목록을 조회합니다.", false);
        add(map, PathItem.HttpMethod.GET, "/api/reference-audios/{audioId}/playback-url", "학습 콘텐츠", "예시 음성 재생 URL 발급", "예시 음성을 제한된 시간 동안 재생할 수 있는 URL을 발급합니다.", false);

        add(map, PathItem.HttpMethod.POST, "/api/training-sessions", "학습 세션", "학습 세션 생성", "학습할 콘텐츠와 학습 초점을 선택해 새로운 녹음·분석 세션을 시작합니다.", false);
        add(map, PathItem.HttpMethod.GET, "/api/training-sessions/{sessionId}", "학습 세션", "학습 세션 상세 조회", "학습 세션의 콘텐츠, 진행 상태, 시간과 선택된 녹음 정보를 조회합니다.", false);
        add(map, PathItem.HttpMethod.POST, "/api/training-sessions/{sessionId}/complete", "학습 세션", "학습 세션 완료", "분석이 끝난 학습 세션을 완료 처리하고 실제 학습 시간을 저장합니다.", false);
        add(map, PathItem.HttpMethod.POST, "/api/training-sessions/{sessionId}/cancel", "학습 세션", "학습 세션 취소", "진행 중인 학습 세션을 취소 상태로 변경합니다.", false);
        add(map, PathItem.HttpMethod.POST, "/api/training-sessions/{sessionId}/recordings/upload-url", "학습 세션", "녹음 업로드 URL 발급", "음성 파일을 스토리지에 직접 업로드할 수 있도록 업로드 URL과 저장 키를 발급합니다.", false);
        add(map, PathItem.HttpMethod.POST, "/api/training-sessions/{sessionId}/recordings", "학습 세션", "업로드한 녹음 등록", "업로드가 끝난 음성 파일의 메타데이터를 학습 세션의 녹음 시도로 등록합니다.", false);
        add(map, PathItem.HttpMethod.GET, "/api/training-sessions/{sessionId}/recordings", "학습 세션", "학습 세션 녹음 목록 조회", "세션에 등록된 녹음 시도와 선택 여부를 생성 순서대로 조회합니다.", false);
        add(map, PathItem.HttpMethod.PATCH, "/api/training-sessions/{sessionId}/recordings/{recordingId}/select", "학습 세션", "분석 대상 녹음 선택", "분석에 사용할 녹음 하나를 선택하고 기존 선택 녹음은 해제합니다.", false);
        add(map, PathItem.HttpMethod.DELETE, "/api/training-sessions/{sessionId}/recordings/{recordingId}", "학습 세션", "녹음 삭제", "내 학습 세션의 녹음을 삭제 처리하며 선택된 녹음이라면 선택 상태도 해제합니다.", false);
        add(map, PathItem.HttpMethod.GET, "/api/recordings/{recordingId}/playback-url", "학습 세션", "내 녹음 재생 URL 발급", "등록된 내 녹음을 제한된 시간 동안 재생할 수 있는 URL을 발급합니다.", false);
        add(map, PathItem.HttpMethod.POST, "/api/training-sessions/{sessionId}/analyze", "학습 세션", "음성 분석 요청", "명시적 동의를 확인하고 선택된 녹음에 대해 Seungun 발음 분석 작업을 요청합니다.", false);
        add(map, PathItem.HttpMethod.GET, "/api/training-sessions/{sessionId}/analysis/status", "학습 세션", "음성 분석 진행 상태 조회", "학습 세션의 최신 분석 작업이 대기, 처리, 완료 또는 실패 중 어느 상태인지 조회합니다.", false);
        add(map, PathItem.HttpMethod.POST, "/api/training-sessions/{sessionId}/analysis/retry", "학습 세션", "실패한 음성 분석 재시도", "새 명시적 동의를 확인하고 실패한 최신 분석 작업을 새 request generation으로 재요청합니다.", false);

        add(map, PathItem.HttpMethod.GET, "/api/analyses/{analysisId}", "분석 결과", "종합 분석 결과 조회", "완료된 분석의 STT 문장, 종합·발음·억양·속도 점수와 강점, 개선점, 종합 피드백을 조회합니다.", false);
        add(map, PathItem.HttpMethod.GET, "/api/analyses/{analysisId}/segments", "분석 결과", "음절별 분석 결과 조회", "분석 결과의 구간별 기대 문장, 인식 문장, 시간 범위, 일치 유형, 점수와 피드백을 페이지로 조회합니다.", false);
        add(map, PathItem.HttpMethod.GET, "/api/training-sessions/{sessionId}/analysis", "분석 결과", "학습 세션 분석 결과 조회", "학습 세션에 선택된 녹음의 최신 분석 ID, 상태와 주요 점수를 조회합니다.", false);
        add(map, PathItem.HttpMethod.POST, "/api/analyses/{analysisId}/feedback/regenerate", "분석 결과", "종합 피드백 재생성", "완료된 분석 결과를 바탕으로 강점, 개선점과 종합 코칭 문구를 다시 생성합니다. 재생성은 분석당 최대 3회 가능합니다.", false);

        add(map, PathItem.HttpMethod.GET, "/api/courses", "클래스", "클래스 목록 조회", "클래스 유형과 검색 조건을 적용해 학습 가능한 클래스 목록을 페이지로 조회합니다.", false);
        add(map, PathItem.HttpMethod.GET, "/api/courses/{courseId}", "클래스", "클래스 상세 조회", "클래스 소개, 전체 단계 수와 현재 사용자의 진행 정보를 조회합니다.", false);
        add(map, PathItem.HttpMethod.GET, "/api/courses/{courseId}/steps", "클래스", "클래스 단계 목록 조회", "클래스를 구성하는 학습 단계를 순서대로 조회합니다.", false);
        add(map, PathItem.HttpMethod.POST, "/api/courses/{courseId}/start", "클래스", "클래스 학습 시작", "사용자의 클래스 진도 레코드를 만들거나 기존 진행 정보를 반환합니다.", false);
        add(map, PathItem.HttpMethod.GET, "/api/courses/{courseId}/progress", "클래스", "클래스 진도 조회", "현재 사용자의 클래스 진행 단계와 완료율을 조회합니다.", false);
        add(map, PathItem.HttpMethod.PATCH, "/api/courses/{courseId}/progress", "클래스", "클래스 진도 수정", "완료한 단계와 학습 시간을 반영해 클래스 진도를 갱신합니다.", false);
        add(map, PathItem.HttpMethod.POST, "/api/courses/{courseId}/complete", "클래스", "클래스 완료", "모든 필수 단계를 마친 클래스를 완료 상태로 변경합니다.", false);
        add(map, PathItem.HttpMethod.GET, "/api/users/me/course-progress", "클래스", "내 클래스 진도 목록 조회", "인증된 사용자가 시작한 클래스별 진행률과 최근 학습 시각을 조회합니다.", false);
        add(map, PathItem.HttpMethod.GET, "/api/users/me/training-sessions", "마이페이지", "학습 기록 목록 조회", "완료된 내 학습 기록을 콘텐츠 유형, 상태와 기간으로 필터링해 최신순 페이지로 조회합니다.", false);
        add(map, PathItem.HttpMethod.GET, "/api/users/me/training-sessions/{sessionId}", "마이페이지", "학습 기록 상세 조회", "내 과거 학습의 콘텐츠, 최종 녹음, STT, 종합 분석과 음절별 피드백을 한 번에 조회합니다.", false);
        add(map, PathItem.HttpMethod.DELETE, "/api/users/me/training-sessions/{sessionId}", "마이페이지", "학습 기록 삭제", "내 학습 세션과 연결된 음절 결과, 분석 결과와 녹음을 트랜잭션으로 삭제합니다.", false);
        add(map, PathItem.HttpMethod.GET, "/api/users/me/statistics", "마이페이지", "학습 통계 조회", "선택 기간의 학습 횟수·시간, 오늘 현황, 연속 학습일과 평균 점수를 계산합니다.", false);
        add(map, PathItem.HttpMethod.GET, "/api/users/me/strengths-weaknesses", "마이페이지", "강점 및 약점 조회", "음절 분석을 발음 항목별로 집계해 반복 시도한 강점과 약점을 점수순으로 제공합니다.", false);
        add(map, PathItem.HttpMethod.GET, "/api/users/me/score-trends", "마이페이지", "점수 변화 추이 조회", "선택한 종합·발음·억양 점수의 날짜별 평균과 학습 횟수를 그래프 데이터로 제공합니다.", false);
        add(map, PathItem.HttpMethod.GET, "/api/users/me/weakness-recommendations", "마이페이지", "약점 기반 추천 조회", "최근 반복된 발음 약점과 연결되는 공개 학습 콘텐츠와 클래스를 추천합니다.", false);
        return Map.copyOf(map);
    }

    record ApiKey(PathItem.HttpMethod method, String path) {}
    record ApiDoc(String tag, String summary, String description, boolean publicApi) {}
}
