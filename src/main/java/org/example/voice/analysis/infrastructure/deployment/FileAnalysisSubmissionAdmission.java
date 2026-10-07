package org.example.voice.analysis.infrastructure.deployment;

import java.nio.file.Files;
import java.nio.file.Path;
import org.example.voice.analysis.domain.port.AnalysisSubmissionAdmission;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class FileAnalysisSubmissionAdmission implements AnalysisSubmissionAdmission {
    private final Path hold;

    public FileAnalysisSubmissionAdmission(
            @Value("${analysis.deployment.hold-file:/var/lib/alpha-deploy/analysis-admission.closed}") String path) {
        this.hold = Path.of(path);
    }

    @Override
    public void assertOpen() {
        // An unreadable marker is unknown, not proof that submissions are open.
        if (!Files.notExists(hold)) throw new BaseException(ErrorCode.ANALYSIS_INTEGRATION_UNAVAILABLE);
    }
}
