package org.example.voice.admin.domain.port;

import org.example.voice.user.domain.entity.User;

public interface AdminUserRoleStore {
    /** Lock actor and target in ID order and refresh any previously managed state. */
    User lock(long actorId, long targetId);
}
