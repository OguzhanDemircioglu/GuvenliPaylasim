package com.server.modules.auth;

import com.server.common.exception.NotFoundException;
import com.server.migration.AdminProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Şifresiz giriş sadece açıkça etkinleştirilince ve sadece bu makineden çalışır.
 */
class DevLoginControllerTest {

    private static final AdminProperties ADMIN = new AdminProperties("admin@test.local", "secret");

    private final AuthService authService = mock(AuthService.class);
    private final RefreshCookies refreshCookies = mock(RefreshCookies.class);
    private final DevLoginController controller = new DevLoginController(authService, ADMIN, refreshCookies);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(AuthService.class, () -> authService)
            .withBean(RefreshCookies.class, () -> refreshCookies)
            .withBean(AdminProperties.class, () -> ADMIN)
            .withUserConfiguration(DevLoginController.class);

    @Test
    void controllerDoesNotExistUnlessEnabled() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(DevLoginController.class));
        contextRunner.withPropertyValues("app.dev-login.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(DevLoginController.class));
        contextRunner.withPropertyValues("app.dev-login.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(DevLoginController.class));
    }

    @Test
    void requestFromAnotherMachineIsRejected() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.20");

        assertThatThrownBy(() -> controller.login(request, new MockHttpServletResponse()))
                .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(authService);
    }

    @Test
    void loopbackRequestLogsInAsConfiguredAdmin() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("0:0:0:0:0:0:0:1");
        when(authService.devLogin(eq(ADMIN.email()), any())).thenReturn(new AuthResult(null, "token"));
        when(refreshCookies.create("token")).thenReturn(org.springframework.http.ResponseCookie.from("c", "v").build());

        controller.login(request, new MockHttpServletResponse());

        verify(authService).devLogin(eq(ADMIN.email()), any());
    }
}
