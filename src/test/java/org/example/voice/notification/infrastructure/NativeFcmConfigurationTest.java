package org.example.voice.notification.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "notification.native-fcm.enabled=true",
    "notification.native-fcm.project-id=test-project",
    "notification.native-fcm.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "notification.native-fcm.poll-interval=PT24H"
})
class NativeFcmConfigurationTest {
    @Autowired NativeFcmDispatcher dispatcher;
    @MockitoBean JdbcTemplate jdbc;

    @Test void workerCanBeConstructedWithConfiguredPropertiesAndTransactions() {
        assertThat(dispatcher).isNotNull();
    }
}
