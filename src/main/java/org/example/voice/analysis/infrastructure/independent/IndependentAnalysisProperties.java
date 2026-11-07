package org.example.voice.analysis.infrastructure.independent;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.time.Duration;
import java.util.List;

@Component @Getter @Setter
@ConfigurationProperties(prefix="analysis.independent")
public class IndependentAnalysisProperties {
    private boolean submissionEnabled;
    private boolean workersEnabled;
    private String endpoint="";
    private List<String> allowedOrigins=List.of();
    private String apiToken="";
    private Duration httpTimeout=Duration.ofSeconds(25);

    public boolean configured() {
        try {
            var uri=URI.create(endpoint);
            return "https".equals(uri.getScheme()) && uri.getHost()!=null && uri.getUserInfo()==null
                    && uri.getRawQuery()==null && uri.getFragment()==null
                    && (uri.getPath().isEmpty() || "/".equals(uri.getPath()))
                    && allowedOrigins.contains(uri.getScheme()+"://"+uri.getRawAuthority())
                    && apiToken.length()>=32 && apiToken.length()<=512
                    && apiToken.chars().allMatch(c->c>32 && c<127)
                    && httpTimeout.toSeconds()>=1 && httpTimeout.toSeconds()<=60;
        } catch (RuntimeException invalid) { return false; }
    }
    public boolean acceptsSubmissions() { return submissionEnabled && configured(); }
    public URI uri(String path) {
        if (!configured()) throw new IndependentTransportException(503,"INDEPENDENT_CONFIGURATION_INVALID",60);
        return URI.create(endpoint.replaceAll("/$","")+path);
    }
}
