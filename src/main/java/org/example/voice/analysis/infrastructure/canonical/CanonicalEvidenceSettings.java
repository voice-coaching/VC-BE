package org.example.voice.analysis.infrastructure.canonical;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Dedicated evidence namespace. Never falls back to the recordings/AWS credential chain. */
@Component
public final class CanonicalEvidenceSettings {
    private final Environment environment;
    private static final String PREFIX = "analysis.canonical.evidence.";
    public CanonicalEvidenceSettings(Environment environment) { this.environment = environment; }

    public boolean registrationEnabled() { return true; }
    public boolean verifierEnabled() { return true; }
    // Installed reader switches, NOT public admission switches. Leave on for retained delivery.
    public boolean callbackEnabled() { return true; }
    public boolean callbackVerifierEnabled() { return true; }
    public boolean journalEnabled() { return true; }
    public boolean callbackApplyEnabled() { return true; }
    public long journalStagingBudgetBytes() {
        try {
            long value=Long.parseLong(environment.getProperty(PREFIX+"journal-staging-budget-bytes","0"));
            return value>=16L*1024*1024 && value<=16L*1024*1024*1024?value:0;
        } catch(NumberFormatException error){return 0;}
    }
    public String bucket() { return value("b2.bucket"); }
    public String prefix() { return value("b2.prefix"); }
    public String region() { return value("b2.region"); }
    public String endpoint() { return value("b2.endpoint"); }
    public String keyId() { return value("b2.key-id"); }
    public String applicationKey() { return value("b2.application-key"); }
    public String verifierPython() { return value("semantic.python"); }
    public String verifierScript() { return value("semantic.script"); }
    public String coreRoot() { return value("semantic.core-root"); }
    public String llmRoot() { return value("semantic.llm-root"); }
    public String recordingsPrefix() { return value("semantic.recordings-prefix"); }
    public boolean semanticConfigured() {
        try { return java.util.stream.Stream.of(verifierPython(), verifierScript(), coreRoot(), llmRoot())
                .allMatch(s -> !s.isBlank() && java.nio.file.Path.of(s).isAbsolute())
                && !recordingsPrefix().isBlank() && recordingsPrefix().endsWith("/");
        } catch(java.nio.file.InvalidPathException error){return false;}
    }
    private String value(String name) { return environment.getProperty(PREFIX + name, ""); }

    public boolean configured() {
        return region().matches("[a-z]{2}-[a-z]+-[0-9]{3}")
                && endpoint().equals("https://s3." + region() + ".backblazeb2.com")
                && bucket().matches("[A-Za-z0-9][A-Za-z0-9-]{4,48}[A-Za-z0-9]")
                && prefix().length() <= 800 && prefix().matches("[A-Za-z0-9][A-Za-z0-9_/-]*/")
                && java.util.Arrays.stream(prefix().split("/", -1))
                    .limit(prefix().split("/", -1).length - 1).allMatch(s -> s.matches("[A-Za-z0-9_-]+"))
                && keyId().matches("[A-Za-z0-9]+") && applicationKey().matches("[A-Za-z0-9]+")
                && "INDEFINITE".equals(value("retention-days"))
                && "INDEFINITE".equals(value("deletion-grace-hours"))
                && "INDEFINITE".equals(value("orphan-grace-hours"));
    }

    public String objectKey(long analysisId, java.util.UUID executionId, String kind, String sha256) {

        return prefix() + analysisId + "/" + executionId + "/" + kind + "/" + sha256 + ".json";
    }
}
