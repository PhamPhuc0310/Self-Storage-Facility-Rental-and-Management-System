package com.safebox.storage.repository;

import com.safebox.storage.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    @EntityGraph(attributePaths = "role")
    Optional<User> findByEmailIgnoreCase(String email);
}
