package org.example.voice.title.infrastructure;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.example.voice.title.domain.TitleRank;
import org.example.voice.title.domain.entity.*;
import org.example.voice.title.domain.port.TitleRepository;
import org.example.voice.training.domain.entity.TrainingSession;
import org.example.voice.training.domain.model.TrainingSessionCreatedData;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.Optional;

@Repository @RequiredArgsConstructor
public class TitlePersistence implements TitleRepository {
    private final EntityManager em;
    private final org.example.voice.analysis.infrastructure.canonical.CanonicalCommittedResultReader committedResults;
    public Optional<UserTitle> title(Long userId) { return Optional.ofNullable(em.find(UserTitle.class, userId)); }
    public UserTitle save(UserTitle title) { if (!em.contains(title)) em.persist(title); em.flush(); return title; }
    public Optional<TitlePolicy> policy(TitleRank target) { return Optional.ofNullable(em.find(TitlePolicy.class, target)); }
    public long completedCount(Long userId) {
        return em.createQuery("select count(s) from TrainingSession s where s.userId=:user and s.status=org.example.voice.training.domain.type.TrainingSessionStatus.COMPLETED", Long.class)
                .setParameter("user", userId).getSingleResult();
    }
    public Optional<TitleExam> owned(Long id, Long userId) {
        return em.createQuery("select e from TitleExam e where e.id=:id and e.userId=:user", TitleExam.class)
                .setParameter("id", id).setParameter("user", userId).getResultStream().findFirst();
    }
    public Optional<TitleExam> active(Long userId, TitleRank target) {
        return em.createQuery("select e from TitleExam e where e.userId=:user and e.targetRank=:target and e.status in ('READY','IN_PROGRESS')", TitleExam.class)
                .setParameter("user", userId).setParameter("target", target).getResultStream().findFirst();
    }
    public TitleExam save(TitleExam exam) { if (exam.getId() == null) em.persist(exam); em.flush(); return exam; }
    public Optional<Long> requested(Long userId, String digest) {
        return em.createQuery("select r.examId from TitleExamRequest r where r.userId=:user and r.keyDigest=:digest", Long.class)
                .setParameter("user", userId).setParameter("digest", digest).getResultStream().findFirst();
    }
    public void remember(TitleExamRequest request) { em.persist(request); }
    public boolean availableContent(Long id) {
        return id != null && em.createQuery("select count(c) from PracticeContent c where c.id=:id and c.status=org.example.voice.practicecontent.domain.type.PublishStatus.PUBLISHED", Long.class)
                .setParameter("id", id).getSingleResult() == 1;
    }
    public Optional<BigDecimal> score(Long analysisId, Long userId, Long sessionId, Long contentId) {
        var canonical = canonicalAnalysis(analysisId, userId, sessionId, contentId);
        if (canonical.isPresent()) return canonicalScore(canonical.get());
        return em.createQuery("""
                select a.overallScore from AnalysisResult a join a.recording r join r.trainingSession s
                where a.id=:id and s.userId=:user and s.id=:session and s.content.id=:content
                and a.status=org.example.voice.analysis.domain.type.AnalysisStatus.COMPLETED
                and a.analysisProfile not in ('CANONICAL_FROZEN_20260928_V4','CANONICAL_HANDOFF_20261004_V5')
                and (a.analysisOutcome is null or a.analysisOutcome in
                    (org.example.voice.analysis.domain.type.AnalysisOutcome.COACHING_READY,
                     org.example.voice.analysis.domain.type.AnalysisOutcome.COMPLETED_NO_ISSUE))
                and r.selected=true and r.deletedAt is null and a.overallScore is not null
                and s.status in (org.example.voice.training.domain.type.TrainingSessionStatus.ANALYZING,
                                 org.example.voice.training.domain.type.TrainingSessionStatus.COMPLETED)
                """, BigDecimal.class).setParameter("id", analysisId).setParameter("user", userId)
                .setParameter("session", sessionId).setParameter("content", contentId).getResultStream().findFirst();
    }
    public boolean ownedAnalysis(Long id, Long userId) {
        return em.createQuery("select count(a) from AnalysisResult a where a.id=:id and a.recording.trainingSession.userId=:user", Long.class)
                .setParameter("id",id).setParameter("user",userId).getSingleResult() == 1;
    }
    public boolean coachingScoreUnavailable(Long analysisId, Long userId, Long sessionId, Long contentId) {
        var canonical = canonicalAnalysis(analysisId, userId, sessionId, contentId);
        if (canonical.isPresent()) return canonicalScore(canonical.get()).isEmpty();
        return em.createQuery("""
                select count(a) from AnalysisResult a join a.recording r join r.trainingSession s
                where a.id=:id and s.userId=:user and s.id=:session and s.content.id=:content
                and a.status=org.example.voice.analysis.domain.type.AnalysisStatus.COMPLETED
                and ((a.coachingDocument is not null and a.overallScore is null)
                    or a.analysisProfile in ('CANONICAL_FROZEN_20260928_V4','CANONICAL_HANDOFF_20261004_V5'))
                and r.selected=true and r.deletedAt is null
                and s.status in (org.example.voice.training.domain.type.TrainingSessionStatus.ANALYZING,
                                 org.example.voice.training.domain.type.TrainingSessionStatus.COMPLETED)
                """, Long.class).setParameter("id", analysisId).setParameter("user", userId)
                .setParameter("session", sessionId).setParameter("content", contentId).getSingleResult() == 1;
    }
    public Optional<TrainingSessionCreatedData> reusableSession(Long sessionId, Long userId) {
        if (sessionId == null) return Optional.empty();
        return em.createQuery("select s from TrainingSession s join fetch s.content where s.id=:id and s.userId=:user and s.status not in (org.example.voice.training.domain.type.TrainingSessionStatus.CANCELED,org.example.voice.training.domain.type.TrainingSessionStatus.FAILED)", TrainingSession.class)
                .setParameter("id", sessionId).setParameter("user", userId).getResultStream().findFirst()
                .map(s -> new TrainingSessionCreatedData(s.getId(), s.getContent().getId(), s.getCourseStepId(), s.getLearningFocus(), s.getStatus(), s.getStartedAt()));
    }

    private Optional<org.example.voice.analysis.domain.entity.AnalysisResult> canonicalAnalysis(
            Long analysisId, Long userId, Long sessionId, Long contentId) {
        return em.createQuery("""
                select a from AnalysisResult a join a.recording r join r.trainingSession s
                where a.id=:id and s.userId=:user and s.id=:session and s.content.id=:content
                and a.analysisProfile='CANONICAL_HANDOFF_20261004_V5'
                and a.status=org.example.voice.analysis.domain.type.AnalysisStatus.COMPLETED
                and r.selected=true and r.deletedAt is null
                and s.status in (org.example.voice.training.domain.type.TrainingSessionStatus.ANALYZING,
                                 org.example.voice.training.domain.type.TrainingSessionStatus.COMPLETED)
                """, org.example.voice.analysis.domain.entity.AnalysisResult.class)
                .setParameter("id", analysisId).setParameter("user", userId)
                .setParameter("session", sessionId).setParameter("content", contentId)
                .getResultStream().findFirst();
    }

    private Optional<BigDecimal> canonicalScore(org.example.voice.analysis.domain.entity.AnalysisResult result) {
        try {
            return committedResults.findCurrent(result)
                    .filter(doc -> doc.hasRequiredStorageEvidence() && doc.decision()!=null
                            && doc.decision().status() == org.example.voice.analysis.infrastructure.canonical.CanonicalCallbackDocument.DecisionStatus.ACCEPT
                            && "INLINE".equals(doc.representation()) && doc.failure()==null
                            && doc.feedbackDeliveryAllowed()
                            && (("READY".equals(doc.adapterStatus())
                                 && ("SCHEMA_AND_STRUCTURAL_SEMANTICS_VALID".equals(doc.generationStatus())
                                     || "DETERMINISTIC_FALLBACK".equals(doc.generationStatus())))
                                || ("NO_PERMITTED_COACHING_CONTENT".equals(doc.adapterStatus())
                                    && "NOT_DISPATCHED".equals(doc.generationStatus()))))
                    .map(doc -> doc.overallScore())
                    .filter(score -> score.signum() >= 0 && score.compareTo(BigDecimal.valueOf(100)) <= 0);
        } catch (IllegalStateException unavailable) {
            return Optional.empty();
        }
    }
}
