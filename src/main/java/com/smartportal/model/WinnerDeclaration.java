package com.smartportal.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(name = "winner_declarations",
        uniqueConstraints = @UniqueConstraint(name = "uk_winner_declaration_entity",
                columnNames = {"entityType", "entityId"}))
public class WinnerDeclaration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String entityType;
    private Long entityId;
    private Long winnerId;
    private LocalDateTime declaredAt;

    public Long getId() { return id; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String value) { this.entityType = value; }
    public Long getEntityId() { return entityId; }
    public void setEntityId(Long value) { this.entityId = value; }
    public Long getWinnerId() { return winnerId; }
    public void setWinnerId(Long value) { this.winnerId = value; }
    public LocalDateTime getDeclaredAt() { return declaredAt; }
    public void setDeclaredAt(LocalDateTime value) { this.declaredAt = value; }
}
