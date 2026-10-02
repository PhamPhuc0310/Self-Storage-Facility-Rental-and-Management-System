package com.safebox.self_storage.repository;

import com.safebox.self_storage.entity.AuthToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface AuthTokenRepository extends JpaRepository<AuthToken, UUID> {
    Optional<AuthToken> findByUserUserIdAndPurpose(UUID userId, String purpose);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AuthToken> findByTokenHashAndPurpose(String tokenHash, String purpose);
}
