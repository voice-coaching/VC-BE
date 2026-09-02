package org.example.voice.analysis.infrastructure.stream;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** All Redis Stream tuning is explicit so cache settings cannot silently define job semantics. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "analysis.stream")
public class AnalysisStreamProperties {

    private boolean enabled;
    private String redisHost = "localhost";
    private int redisPort = 6379;
    private String redisUsername;
    private String redisPassword;
    private boolean redisSslEnabled;
    private Duration redisConnectTimeout = Duration.ofSeconds(5);
    private Duration redisCommandTimeout = Duration.ofSeconds(5);
    private Duration redisShutdownTimeout = Duration.ofSeconds(1);
    private String requestStream = "analysis:request:v1";
    private String resultStream = "analysis:result:v1";
    private String resultConsumerGroup = "backend-analysis-result-workers";
    private String resultConsumerName = "backend-analysis-result-1";
    private String resultDeadLetterStream = "analysis:result:dlq:v1";
    private Duration outboxPollInterval = Duration.ofSeconds(1);
    private Duration resultPollInterval = Duration.ofSeconds(1);
    private Duration resultBlock = Duration.ofSeconds(1);
    private Duration pendingClaimIdle = Duration.ofMinutes(5);
    private int batchSize = 25;
    private int maxRetries = 3;
    private int maximumPayloadBytes = 65_536;
    private long deadLetterMaximumLength = 10_000;
    private int maxConcurrentPerUser = 3;
    private Duration executionTimeout = Duration.ofMinutes(15);
    private Duration timeoutSweepInterval = Duration.ofMinutes(1);
    private int timeoutSweepBatchSize = 100;
}
