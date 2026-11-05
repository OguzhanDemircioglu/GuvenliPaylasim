package com.server.modules.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findBySenderIdOrderByCreatedAtDesc(Long senderId);
}
