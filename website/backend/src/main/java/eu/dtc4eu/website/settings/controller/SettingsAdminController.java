package eu.dtc4eu.website.settings.controller;

import eu.dtc4eu.website.settings.service.SettingsService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
public class SettingsAdminController {

    private final SettingsService settingsService;

    @Operation(summary = "All site settings keyed by setting key")
    @GetMapping
    public Map<String, Map<String, Object>> all() {
        return settingsService.all();
    }

    @Operation(summary = "Upsert one settings key")
    @PutMapping("/{key}")
    public void put(@PathVariable String key, @RequestBody Map<String, Object> value) {
        settingsService.put(key, value);
    }
}
