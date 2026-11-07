package org.example.voice.admin.controller.dto;

import org.example.voice.admin.domain.model.AdminTitleExamPolicyView;
import org.example.voice.title.domain.TitleRank;

public record AdminTitleExamPolicyResponseDto(TitleRank targetRank, String targetTitle, long version,
        int passingScore, long requiredTrainingCount, Long practiceContentId, boolean available,
        AdminResponseDto.ContentDto content) {
    public static AdminTitleExamPolicyResponseDto from(AdminTitleExamPolicyView p) {
        return new AdminTitleExamPolicyResponseDto(p.targetRank(), p.targetTitle(), p.version(), p.passingScore(),
                p.requiredTrainingCount(), p.practiceContentId(), p.available(), p.content() == null ? null : AdminResponseDto.ContentDto.from(p.content()));
    }
}
