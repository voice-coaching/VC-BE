package org.example.voice.analysis.infrastructure.canonical;

import org.example.voice.analysis.infrastructure.runpod.RunPodContract;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.assertj.core.api.Assertions.assertThat;

class CanonicalDeliverySpoolStartupTest {
    @TempDir Path directory;

    @Test void enabledSpoolOpensOwnershipChannelAndReleasesItOnClose() throws Exception {
        var env = new MockEnvironment()
                .withProperty("analysis.canonical.delivery.enabled", "true")
                .withProperty("analysis.canonical.delivery.directory", directory.toString());
        var lockPath = directory.resolve("owner.lock");
        var spool = new CanonicalDeliverySpool(env, new RunPodContract());
        try {
            spool.start();
            assertThat(spool.operational()).isTrue();
            assertThat(Files.isRegularFile(lockPath)).isTrue();
            assertThat(spool.entries()).isEmpty();
        } finally {
            spool.close();
        }
        assertThat(spool.operational()).isFalse();
        try (var channel = FileChannel.open(lockPath, StandardOpenOption.WRITE);
             var lock = channel.tryLock()) {
            assertThat(lock).isNotNull();
        }
        var restarted = new CanonicalDeliverySpool(env, new RunPodContract());
        try {
            restarted.start();
            assertThat(restarted.operational()).isTrue();
        } finally {
            restarted.close();
        }
    }

    @Test void disabledSpoolDoesNotCreateOwnershipFiles() throws Exception {
        var env = new MockEnvironment()
                .withProperty("analysis.canonical.delivery.enabled", "false")
                .withProperty("analysis.canonical.delivery.directory", directory.toString());
        var spool = new CanonicalDeliverySpool(env, new RunPodContract());
        try {
            spool.start();
            assertThat(spool.operational()).isTrue();
            assertThat(Files.exists(directory.resolve("owner.lock"))).isFalse();
        } finally {
            spool.close();
        }
    }
}
