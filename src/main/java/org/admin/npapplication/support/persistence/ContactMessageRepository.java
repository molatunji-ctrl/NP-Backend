package org.admin.npapplication.support.persistence;

import org.admin.npapplication.support.domain.ContactMessage;
import org.admin.npapplication.support.domain.MessageStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContactMessageRepository extends JpaRepository<ContactMessage, Long> {
    Page<ContactMessage> findAll(Pageable pageable);
    Page<ContactMessage> findByStatus(MessageStatus status, Pageable pageable);
    Long countByStatus(MessageStatus status);
}