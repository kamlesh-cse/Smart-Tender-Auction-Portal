package com.smartportal.repository;

import com.smartportal.model.TenderDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TenderDocumentRepository extends JpaRepository<TenderDocument, Long> {
    List<TenderDocument> findByTenderId(Long tenderId);
}
