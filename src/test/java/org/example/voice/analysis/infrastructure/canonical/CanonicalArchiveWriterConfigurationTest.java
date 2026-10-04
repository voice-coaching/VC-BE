package org.example.voice.analysis.infrastructure.canonical;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.assertThat;

class CanonicalArchiveWriterConfigurationTest {
    @Test
    void acceptsOpaqueProviderKeyWithoutDiscardingPunctuation() {
        var env = new MockEnvironment();
        String base = "analysis.canonical.handoff.";
        env.withProperty(base + "b2.region", "us-east-005")
                .withProperty(base + "b2.endpoint", "https://s3.us-east-005.backblazeb2.com")
                .withProperty(base + "b2.bucket", "private-test-bucket")
                .withProperty(base + "b2.prefix", "canonical-evidence/v5/")
                .withProperty(base + "b2.writer-key-id", "testkeyid")
                .withProperty(base + "retention", "INDEFINITE");
        var writer = new CanonicalArchiveWriter(new CanonicalHandoffSettings(env, null));
        String key = base + "b2.writer-application-key";
        // Pure settings validation: no S3 request or credential fixture.
        for (String value : new String[]{"example-key+with/slash=", "plainTestKey123"}) {
            env.setProperty(key, value);
            assertThat(writer.configured()).isTrue();
        }
        for (String value : new String[]{"", "unsafe\nkey", "unsafe key", "unsafe\u0000key", "a".repeat(513)}) {
            env.setProperty(key, value);
            assertThat(writer.configured()).isFalse();
        }
    }
}
