package org.example.voice.admin.infrastructure;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.example.voice.admin.domain.port.AdminUserRoleStore;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.user.domain.entity.User;
import org.springframework.stereotype.Repository;

@Repository @RequiredArgsConstructor
public class AdminUserRolePersistence implements AdminUserRoleStore {
    private final EntityManager em;

    @Override public User lock(long actorId, long targetId) {
        var users=em.createQuery("select u from User u where u.id in (:actor,:target) order by u.id",User.class)
                .setParameter("actor",actorId).setParameter("target",targetId)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
        users.forEach(user->em.refresh(user,LockModeType.PESSIMISTIC_WRITE));
        return users.stream().filter(user->user.getId()==targetId).findFirst()
                .orElseThrow(()->new BaseException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
