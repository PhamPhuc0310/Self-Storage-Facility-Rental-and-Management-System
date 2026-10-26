package com.safebox.self_storage.repository;
import com.safebox.self_storage.entity.SupportRequestMessage;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SupportRequestMessageRepository extends JpaRepository<SupportRequestMessage,UUID>{ List<SupportRequestMessage> findByRequestIdOrderByCreatedAtAsc(UUID requestId); }
