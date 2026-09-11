package org.example.voice.analysis.infrastructure.authorization;

import org.example.voice.analysis.domain.model.AnalysisAuthorizationGrant;
import org.example.voice.analysis.domain.model.AnalysisAuthorizationIssue;
import org.example.voice.analysis.domain.port.AnalysisAuthorizationIssuer;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression("'${analysis.transport:disabled}' == 'disabled' and '${analysis.stream.enabled:false}' == 'false'")
public class DisabledAnalysisAuthorizationIssuer implements AnalysisAuthorizationIssuer {
    @Override
    public AnalysisAuthorizationGrant issue(AnalysisAuthorizationIssue issue) {
        throw new BaseException(ErrorCode.ANALYSIS_INTEGRATION_UNAVAILABLE);
    }
}
