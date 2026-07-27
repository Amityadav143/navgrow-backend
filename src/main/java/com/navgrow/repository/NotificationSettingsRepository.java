/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 */
package com.navgrow.repository;

import com.navgrow.entity.NotificationSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationSettingsRepository extends JpaRepository<NotificationSettings, Integer> {
}
