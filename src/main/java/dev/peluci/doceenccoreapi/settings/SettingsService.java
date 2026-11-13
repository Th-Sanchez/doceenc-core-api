package dev.peluci.doceenccoreapi.settings;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SettingsService {

    private final SettingsRepository settings;

    public SettingsService(SettingsRepository settings) {
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public SettingsResponse get() {
        return SettingsResponse.from(load());
    }

    @Transactional(readOnly = true)
    public TargetMargins targetMargins() {
        Settings current = load();
        return new TargetMargins(current.getTargetMarginRetail(), current.getTargetMarginWholesale());
    }

    public SettingsResponse update(UpdateSettingsRequest request) {
        Settings current = load();
        current.changeTargetMargins(request.targetMarginRetail(), request.targetMarginWholesale());
        return SettingsResponse.from(settings.saveAndFlush(current));
    }

    private Settings load() {
        return settings.findById(Settings.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("Settings row is missing; the V11 migration creates it."));
    }
}
