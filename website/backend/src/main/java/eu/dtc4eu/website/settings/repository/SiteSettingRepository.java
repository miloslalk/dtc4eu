package eu.dtc4eu.website.settings.repository;

import eu.dtc4eu.website.settings.entity.SiteSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SiteSettingRepository extends JpaRepository<SiteSetting, Long> {

    Optional<SiteSetting> findBySettingKey(String settingKey);
}
