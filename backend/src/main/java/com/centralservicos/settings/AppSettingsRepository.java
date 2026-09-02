package com.centralservicos.settings;

import org.springframework.data.jpa.repository.JpaRepository;

interface AppSettingsRepository extends JpaRepository<AppSettings, Integer> {
}
