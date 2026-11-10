package org.example.voice.admin.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.admin.domain.port.*;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class PopupImageService {
    private final AdminAccessService access;
    private final PopupImageTransactions transactions;
    private final PopupImageProcessor processor;
    private final PopupImageStorage storage;
    public void upload(long actor,long popup,long version,String reason,byte[] source) {
        access.require(actor);
        var png=processor.png(source);
        // Reserve a durable cleanup record before external I/O; failed uploads/commits remain reclaimable.
        var reserved=transactions.reserve(actor,popup,version,reason);
        storage.upload(reserved.objectKey(),png);
        transactions.attach(actor,popup,version,reserved.id(),reason);
    }
}
