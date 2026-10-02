package com.safebox.self_storage.repository;

import com.safebox.self_storage.entity.StorageUnitType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StorageUnitTypeRepository extends JpaRepository<StorageUnitType, Integer> {
}
