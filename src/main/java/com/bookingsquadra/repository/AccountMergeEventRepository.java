package com.bookingsquadra.repository;

import com.bookingsquadra.entity.AccountMergeEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccountMergeEventRepository extends JpaRepository<AccountMergeEvent, UUID> {
}
