/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 * Unauthorised copying or distribution is strictly prohibited.
 */
package com.navgrow.repository;

import com.navgrow.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    Optional<User> findByEmailIgnoreCase(String email);
    // Case-insensitive helpers that can never throw on legacy "A@x.com"/"a@x.com" pairs.
    Optional<User> findFirstByEmailIgnoreCaseOrderByCreatedAtAsc(String email);
    boolean existsByEmailIgnoreCase(String email);
    Optional<User> findByPhone(String phone);
    boolean existsByEmail(String email);

    // Super-admin bootstrap (role bound as a parameter — safe with Postgres enums).
    long countByRole(com.navgrow.enums.UserRole role);
    Optional<User> findFirstByRoleAndActiveTrueOrderByCreatedAtAsc(com.navgrow.enums.UserRole role);
    Page<User> findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
        String name, String email, Pageable pageable);
}