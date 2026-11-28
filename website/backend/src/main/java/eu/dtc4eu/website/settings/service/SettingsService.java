package eu.dtc4eu.website.settings.service;

import eu.dtc4eu.website.common.error.BadRequestException;
import eu.dtc4eu.website.common.web.SafeUrl;
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

    /** Link fields the public site renders as href (e.g. platform.url) must pass {@link SafeUrl}. */
    @Transactional
    public void put(String key, Map<String, Object> value) {
        value.forEach((field, fieldValue) -> {
            boolean link = field.equals("url") || field.endsWith("Url") || field.endsWith("Href");
            if (link && fieldValue instanceof String s && !s.isBlank() && !SafeUrl.isSafe(s.trim())) {
                throw new BadRequestException(key + "." + field + " must be a /path, #anchor, http(s):// or mailto: link");
            }
        });
        SiteSetting setting = siteSettingRepository.findBySettingKey(key).orElseGet(() -> {
            SiteSetting created = new SiteSetting();
            created.setSettingKey(key);
            return created;
        });
        setting.setValue(value);
        siteSettingRepository.save(setting);
    }
}
