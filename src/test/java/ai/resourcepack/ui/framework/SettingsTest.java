package ai.resourcepack.ui.framework;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsTest {
    @Test
    void viewVisibilityAndDamageKeepTheirExistingDefaults() {
        Settings settings = Settings.load(new YamlConfiguration());

        assertTrue(settings.invisible);
        assertTrue(settings.closeOnDamage);
    }

    @Test
    void viewVisibilityAndDamageCanBeDisabledIndependently() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("views.invisible", false);
        config.set("views.close-on-damage", false);

        Settings settings = Settings.load(config);

        assertFalse(settings.invisible);
        assertFalse(settings.closeOnDamage);
    }
}
