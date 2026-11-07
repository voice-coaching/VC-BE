package org.example.voice.admin.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.admin.domain.entity.AdminAuditLog;
import org.example.voice.admin.domain.model.AdminData.ContentView;
import org.example.voice.admin.domain.model.AdminTitleExamPolicyView;
import org.example.voice.admin.domain.port.AdminStore;
import org.example.voice.admin.domain.port.AdminTitleExamPolicyStore;
import org.example.voice.practicecontent.domain.type.*;
import org.example.voice.title.domain.TitleRank;
import org.example.voice.title.domain.entity.TitlePolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class AdminTitleExamPolicyService {
    private final AdminAccessService access;
    private final AdminStore contents;
    private final AdminTitleExamPolicyStore policies;
    private final Clock clock;

    public List<AdminTitleExamPolicyView> list(long actor) {
        access.require(actor);
        return policies.policies().stream().filter(p -> p.getTargetRank() != TitleRank.ABSOLUTE_BEGINNER)
                .sorted(Comparator.comparing(TitlePolicy::getTargetRank)).map(this::view).toList();
    }

    @Transactional
    public AdminTitleExamPolicyView edit(long actor, TitleRank rank, long version, long contentId, long contentVersion, int score, String reason) {
        access.require(actor); AdminValidation.text(reason, 500, true);
        if (rank == null || rank == TitleRank.ABSOLUTE_BEGINNER || contentId <= 0 || score < 1 || score > 100)
            AdminValidation.invalid();
        // Use the same content lock as publication/deletion so only usable questions can be linked.
        var content = contents.lockContent(contentId);
        AdminValidation.version(content.getVersion(), contentVersion);
        if (!eligible(contents.view(content))) AdminValidation.invalid();
        var policy = policies.lockPolicy(rank);
        AdminValidation.version(policy.getEditVersion(), version);
        policy.configure(contentId, score);
        contents.audit(AdminAuditLog.success(actor, "TITLE_EXAM_POLICY_EDIT", "TITLE_EXAM_POLICY", rank.name(),
                reason, "practiceContentId,passingScore,version", OffsetDateTime.now(clock)));
        contents.flush();
        return view(policy);
    }

    private AdminTitleExamPolicyView view(TitlePolicy policy) {
        ContentView content = policy.getPracticeContentId() == null ? null : contents.content(policy.getPracticeContentId()).orElse(null);
        return new AdminTitleExamPolicyView(policy.getTargetRank(), policy.getTargetRank().label(), policy.getEditVersion(),
                policy.getPassingScore(), policy.getRequiredTrainingCount(), policy.getPracticeContentId(), eligible(content), content);
    }
    private boolean eligible(ContentView content) {
        return content != null && content.type() == ContentType.SENTENCE && "TITLE_EXAM".equals(content.category())
                && content.status() == PublishStatus.PUBLISHED && content.scriptText() != null && !content.scriptText().isBlank();
    }
}
