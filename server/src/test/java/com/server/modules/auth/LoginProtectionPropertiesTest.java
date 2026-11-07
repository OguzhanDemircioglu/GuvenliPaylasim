package com.server.modules.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Ayar Spring'in kurduğu yoldan bağlanabilmeli (record'a ikinci kurucu eklemek açılışı bozar). */
class LoginProtectionPropertiesTest {

    @Test
    void bindsFromApplicationSettings() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
                "app.login-protection.max-attempts", "5",
                "app.login-protection.block-duration", "1h",
                "app.login-protection.phone-max-attempts", "30")));

        LoginProtectionProperties properties = binder.bind("app.login-protection", LoginProtectionProperties.class).get();

        assertThat(properties).isEqualTo(new LoginProtectionProperties(5, Duration.ofHours(1), 30));
    }
}
