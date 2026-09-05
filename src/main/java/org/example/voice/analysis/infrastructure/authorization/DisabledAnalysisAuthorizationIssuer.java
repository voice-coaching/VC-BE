package org.example.voice.analysis.infrastructure.authorization;

import org.example.voice.analysis.domain.model.AnalysisAuthorizationGrant;
import org.example.voice.analysis.domain.model.AnalysisAuthorizationIssue;
import org.example.voice.analysis.domain.port.AnalysisAuthorizationIssuer;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "analysis.stream",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class DisabledAnalysisAuthorizationIssuer implements AnalysisAuthorizationIssuer {
    @Override
    public AnalysisAuthorizationGrant issue(AnalysisAuthorizationIssue issue) {
        throw new BaseException(ErrorCode.ANALYSIS_INTEGRATION_UNAVAILABLE);
    }
}
