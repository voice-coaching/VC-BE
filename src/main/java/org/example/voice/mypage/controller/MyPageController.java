package org.example.voice.mypage.controller;

import lombok.RequiredArgsConstructor;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.common.security.LoginUser;
import org.example.voice.mypage.application.MyPageService;
import org.example.voice.mypage.controller.dto.MyPageResponses;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/me")
public class MyPageController {
    private final MyPageService service;

    @GetMapping("/training-sessions")
    public ApiResponse<MyPageResponses.HistoryList> getHistory(@AuthenticationPrincipal LoginUser user,
            @RequestParam(required = false) String type, @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success("학습 기록을 조회했습니다.",
                MyPageResponses.HistoryList.from(service.getHistory(user.id(), type, status, from, to, page, size)));
    }

    @GetMapping("/training-sessions/{sessionId}")
    public ApiResponse<MyPageResponses.HistoryDetail> getHistoryDetail(@AuthenticationPrincipal LoginUser user,
            @PathVariable Long sessionId) {
        return ApiResponse.success("학습 기록 상세 정보를 조회했습니다.",
                MyPageResponses.HistoryDetail.from(service.getHistoryDetail(user.id(), sessionId)));
    }

    @DeleteMapping("/training-sessions/{sessionId}")
    public ApiResponse<Void> deleteHistory(@AuthenticationPrincipal LoginUser user, @PathVariable Long sessionId) {
        service.deleteHistory(user.id(), sessionId);
        return ApiResponse.success("학습 기록이 삭제되었습니다.", null);
    }

    @GetMapping("/statistics")
    public ApiResponse<MyPageResponses.Statistics> getStatistics(@AuthenticationPrincipal LoginUser user,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success("학습 통계를 조회했습니다.",
                MyPageResponses.Statistics.from(service.getStatistics(user.id(), period, from, to)));
    }

    @GetMapping("/strengths-weaknesses")
    public ApiResponse<MyPageResponses.StrengthsWeaknesses> getStrengthsWeaknesses(
            @AuthenticationPrincipal LoginUser user, @RequestParam(required = false) String period,
            @RequestParam(defaultValue = "5") int limit) {
        return ApiResponse.success("강점과 약점을 조회했습니다.",
                MyPageResponses.StrengthsWeaknesses.from(service.getStrengthsWeaknesses(user.id(), period, limit)));
    }

    @GetMapping("/score-trends")
    public ApiResponse<MyPageResponses.ScoreTrend> getScoreTrend(@AuthenticationPrincipal LoginUser user,
            @RequestParam String metric, @RequestParam(required = false) String period) {
        return ApiResponse.success("점수 변화 추이를 조회했습니다.",
                MyPageResponses.ScoreTrend.from(service.getScoreTrend(user.id(), metric, period)));
    }

    @GetMapping("/weakness-recommendations")
    public ApiResponse<MyPageResponses.WeaknessRecommendations> getWeaknessRecommendations(
            @AuthenticationPrincipal LoginUser user, @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String contentType) {
        return ApiResponse.success("약점 기반 추천을 조회했습니다.", MyPageResponses.WeaknessRecommendations.from(
                service.getWeaknessRecommendations(user.id(), limit, contentType)));
    }
}
