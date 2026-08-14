package org.example.voice.user.controller;

import org.example.voice.common.security.LoginUser;
import org.example.voice.home.application.RecentLearningService;
import org.example.voice.user.application.UserService;
import org.example.voice.user.controller.dto.UserProfileUpdateRequestDto;
import org.example.voice.user.domain.model.UpdatedUserProfile;
import org.example.voice.user.domain.model.UserProfile;
import org.example.voice.user.domain.model.WithdrawalResult;
import org.example.voice.user.domain.type.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class UserControllerTest {

    private UserService userService;
    private UserController controller;
    private final LoginUser loginUser = new LoginUser(1L);

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        controller = new UserController(userService, mock(RecentLearningService.class));
    }

    @Test
    void getMyProfileUsesAuthenticatedUser() {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-14T00:00:00Z");
        when(userService.getMyProfile(1L)).thenReturn(
                new UserProfile(1L, "user@example.com", "또박이", UserStatus.ACTIVE,
                        List.of("GOOGLE"), true, now));

        var response = controller.getMyProfile(loginUser);

        assertThat(response.isResult()).isTrue();
        assertThat(response.getData().id()).isEqualTo(1L);
        assertThat(response.getMessage()).isEqualTo("내 정보를 조회했습니다.");
    }

    @Test
    void updateMyProfileReturnsSpecificationResponse() {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-14T00:00:00Z");
        when(userService.updateMyProfile(1L, "새닉네임"))
                .thenReturn(new UpdatedUserProfile(1L, "새닉네임", now));

        var response = controller.updateMyProfile(loginUser, new UserProfileUpdateRequestDto("새닉네임"));

        assertThat(response.getData().nickname()).isEqualTo("새닉네임");
        assertThat(response.getMessage()).isEqualTo("프로필이 수정되었습니다.");
    }

    @Test
    void withdrawReturnsWithdrawalTime() {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-14T00:00:00Z");
        when(userService.withdraw(1L)).thenReturn(new WithdrawalResult(now));

        var response = controller.withdraw(loginUser);

        assertThat(response.getData().withdrawnAt()).isEqualTo(now);
        assertThat(response.getMessage()).isEqualTo("회원 탈퇴가 완료되었습니다.");
    }
}
