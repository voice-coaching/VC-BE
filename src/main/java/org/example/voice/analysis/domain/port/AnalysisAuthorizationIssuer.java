package org.example.voice.analysis.domain.port;

import org.example.voice.analysis.domain.model.AnalysisAuthorizationGrant;
import org.example.voice.analysis.domain.model.AnalysisAuthorizationIssue;

public interface AnalysisAuthorizationIssuer {
    AnalysisAuthorizationGrant issue(AnalysisAuthorizationIssue issue);
}
