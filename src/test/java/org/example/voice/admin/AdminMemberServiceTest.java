package org.example.voice.admin;
import org.example.voice.admin.application.*;
import org.example.voice.admin.domain.port.AdminMemberStore;
import org.example.voice.admin.domain.type.AdminUserResource;
import org.example.voice.coaching.application.CoachingUsageService;
import org.example.voice.common.exception.BaseException;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class AdminMemberServiceTest {
    @Test void resourceReadsAuthorizeWithoutRepeatingMembershipAndLoginProviderQueries() {
        var access=mock(AdminAccessService.class);var reads=mock(AdminReadService.class);var store=mock(AdminMemberStore.class);var coaching=mock(CoachingUsageService.class);
        var service=new AdminMemberService(access,reads,store,coaching);when(store.visibleMember(2)).thenReturn(true);
        service.records(1,2,AdminUserResource.TRAINING_SESSIONS,0,20);
        verify(access).require(1);verify(store).records(2,AdminUserResource.TRAINING_SESSIONS,0,20);verifyNoInteractions(reads,coaching);
        when(store.visibleMember(2)).thenReturn(false);
        assertThatThrownBy(()->service.records(1,2,AdminUserResource.TRAINING_SESSIONS,0,20)).isInstanceOf(BaseException.class);
        verify(store,times(1)).records(2,AdminUserResource.TRAINING_SESSIONS,0,20);
    }
}
