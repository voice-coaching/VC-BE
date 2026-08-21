package org.example.voice.common.cache;

import java.time.Duration;
import java.util.Map;

public interface CacheTtlProvider {

    Map<String, Duration> cacheTtls();
}
