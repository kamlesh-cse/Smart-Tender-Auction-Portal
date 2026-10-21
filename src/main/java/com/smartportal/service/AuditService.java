package com.smartportal.service;

import com.smartportal.model.AuditRecord;
import com.smartportal.repository.AuditRecordRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final AuditRecordRepository repository;

    public AuditService(AuditRecordRepository repository) {
        this.repository = repository;
    }

    public void record(String actorId, String action, String entity, String metadata) {
        record(actorId, action, entity, metadata, null);
    }

    public void record(String actorId, String action, String entity, String metadata, String ipAddress) {
        AuditRecord record = new AuditRecord();
        record.setActorId(actorId != null ? actorId : "SYSTEM");
        record.setAction(action);
        record.setEntity(entity);
        record.setTimestamp(LocalDateTime.now());
        record.setMetadata(metadata);
        record.setIpAddress(ipAddress);
        repository.save(record);
    }
}
