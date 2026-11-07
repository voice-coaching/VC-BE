package org.example.voice.admin.domain.port;

import org.example.voice.title.domain.TitleRank;
import org.example.voice.title.domain.entity.TitlePolicy;
import java.util.List;

public interface AdminTitleExamPolicyStore {
    List<TitlePolicy> policies();
    TitlePolicy lockPolicy(TitleRank rank);
    boolean referencedContent(long contentId);
}
