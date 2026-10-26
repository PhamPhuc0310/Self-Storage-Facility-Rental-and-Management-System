package com.safebox.self_storage.repository;
import com.safebox.self_storage.entity.ActivityLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ActivityLogRepository extends JpaRepository<ActivityLog,UUID>{}
