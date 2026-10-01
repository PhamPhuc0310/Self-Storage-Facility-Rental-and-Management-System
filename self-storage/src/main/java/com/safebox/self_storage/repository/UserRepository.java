package com.safebox.self_storage.repository;

import com.safebox.self_storage.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {
    @EntityGraph(attributePaths = "role")
    Optional<User> findByEmailIgnoreCase(String email);

    @Override
    @EntityGraph(attributePaths = "role")
    Optional<User> findById(UUID id);
}
