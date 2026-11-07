package org.example.voice.admin.infrastructure;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.example.voice.admin.domain.port.AdminTitleExamPolicyStore;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.title.domain.TitleRank;
import org.example.voice.title.domain.entity.TitlePolicy;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository @RequiredArgsConstructor
public class AdminTitleExamPolicyPersistence implements AdminTitleExamPolicyStore {
    private final EntityManager em;
    public List<TitlePolicy> policies() {
        return em.createQuery("select p from TitlePolicy p", TitlePolicy.class).getResultList();
    }
    public TitlePolicy lockPolicy(TitleRank rank) {
        var policy = em.find(TitlePolicy.class, rank, LockModeType.PESSIMISTIC_WRITE);
        if (policy == null) throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND);
        return policy;
    }
    public boolean referencedContent(long id) {
        return em.createQuery("select count(p) from TitlePolicy p where p.practiceContentId=:id", Long.class)
                .setParameter("id", id).getSingleResult() > 0
                || em.createQuery("select count(e) from TitleExam e where e.practiceContentId=:id and e.status in ('READY','IN_PROGRESS')", Long.class)
                .setParameter("id", id).getSingleResult() > 0;
    }
}
