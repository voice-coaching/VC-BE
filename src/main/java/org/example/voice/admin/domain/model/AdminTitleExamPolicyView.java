package org.example.voice.admin.domain.model;

import org.example.voice.title.domain.TitleRank;

public record AdminTitleExamPolicyView(TitleRank targetRank, String targetTitle, long version,
        int passingScore, long requiredTrainingCount, Long practiceContentId, boolean available,
        AdminData.ContentView content) {}
