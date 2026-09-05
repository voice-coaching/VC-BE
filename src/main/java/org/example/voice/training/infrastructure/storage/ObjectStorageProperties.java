package org.example.voice.training.infrastructure.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "storage")
public class ObjectStorageProperties {
    private boolean enabled;
    private String bucket;
    private String region;
    private String endpoint;
    private boolean pathStyleAccess;
    private String recordingsPrefix = "recordings/";
}
