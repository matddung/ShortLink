package com.studyjun.backend.link;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RedirectClickOutboxRepository extends JpaRepository<RedirectClickOutbox, Long> {
    List<RedirectClickOutbox> findTop100ByStatusOrderByIdAsc(RedirectClickOutbox.Status status);
}