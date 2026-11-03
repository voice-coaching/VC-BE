package org.example.voice.payment.domain;
import java.util.*;
public interface MembershipReader {
    Membership read(Long userId);
    default Map<Long,Membership> readAll(Collection<Long> userIds) {
        var result=new LinkedHashMap<Long,Membership>();
        userIds.forEach(id->result.put(id,read(id)));return result;
    }
}
