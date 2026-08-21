package eu.dtc4eu.website.settings.service;

import eu.dtc4eu.website.settings.entity.SiteSetting;
import eu.dtc4eu.website.settings.repository.SiteSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private final SiteSettingRepository siteSettingRepository;

    @Transactional(readOnly = true)
    public Map<String, Map<String, Object>> all() {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (SiteSetting setting : siteSettingRepository.findAll()) {
            result.put(setting.getSettingKey(), setting.getValue());
        }
        return result;
    }

    @Transactional
    public void put(String key, Map<String, Object> value) {
        SiteSetting setting = siteSettingRepository.findBySettingKey(key).orElseGet(() -> {
            SiteSetting created = new SiteSetting();
            created.setSettingKey(key);
            return created;
        });
        setting.setValue(value);
        siteSettingRepository.save(setting);
    }
}
