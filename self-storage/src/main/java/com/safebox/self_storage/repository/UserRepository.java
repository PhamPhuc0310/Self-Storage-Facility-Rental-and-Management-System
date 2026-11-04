package com.safebox.self_storage.repository;

import com.safebox.self_storage.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.userId = :id")
    Optional<User> lockById(@org.springframework.data.repository.query.Param("id") UUID id);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.role.roleName = 'SYSTEM_ADMIN' and u.status = 'ACTIVE'")
    java.util.List<User> lockActiveAdministrators();
    @EntityGraph(attributePaths = "role")
    Optional<User> findByEmailIgnoreCase(String email);

    @Override
    @EntityGraph(attributePaths = "role")
    Optional<User> findById(UUID id);
}
