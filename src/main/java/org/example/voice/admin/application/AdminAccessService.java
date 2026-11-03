package org.example.voice.admin.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.admin.domain.model.AdminData.Access;
import org.example.voice.admin.domain.port.AdminStore;
import org.example.voice.common.exception.*;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class AdminAccessService {
    private final AdminStore store;
    @Transactional(readOnly=true)
    public Access require(long userId) {
        try {
            return store.activeAdmin(userId).orElseThrow(() -> new BaseException(ErrorCode.ACCESS_DENIED));
        } catch (DataAccessException error) {
            throw new BaseException(ErrorCode.ADMIN_STORE_UNAVAILABLE);
        }
    }
}
