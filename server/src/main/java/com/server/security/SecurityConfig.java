package com.server.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Set;

/**
 * Varsayılan kural: USER rolü (hiyerarşiyle ADMIN ve SUPERADMIN) erişir; OWNER sadece auth, rapor ve /api/insights
 * yollarına erişir. Admin'e özel endpoint'ler ayrıca @PreAuthorize ile işaretlenir.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /** Token gerektirmeyen auth endpoint'leri. */
    private static final Set<String> PUBLIC_AUTH_PATHS = Set.of(
            "/api/auth/login", "/api/auth/register", "/api/auth/refresh", "/api/auth/logout", "/api/auth/dev-login");

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   RestAuthenticationEntryPoint authenticationEntryPoint,
                                                   RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, PUBLIC_AUTH_PATHS.toArray(String[]::new)).permitAll()
                        .requestMatchers("/actuator/health/**", "/error").permitAll()
                        // Swagger yalnızca dev profilinde açıktır (springdoc ayarı); prod'da bu yollar 404 döner.
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // Oturum, şifre değiştirme vb.: her rol.
                        .requestMatchers("/api/auth/**").authenticated()
                        // Yol kuralı ilk katmandır; controller'lar ayrıca @PreAuthorize taşır (çift katman).
                        .requestMatchers("/api/system/**").hasRole(Role.SUPERADMIN.name())
                        .requestMatchers("/api/insights/**").hasRole(Role.OWNER.name())
                        // Toplu raporlar sahibe de açıktır.
                        .requestMatchers("/api/reports/**").hasAnyRole(Role.USER.name(), Role.OWNER.name())
                        // Varsayılan: USER (hiyerarşiyle ADMIN ve SUPERADMIN). OWNER buraya giremez.
                        .anyRequest().hasRole(Role.USER.name()))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(bearerTokenResolver())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    /**
     * Role enum'undaki hiyerarşi Spring Security'ye verilir; hem yol kuralları hem @PreAuthorize bunu kullanır.
     * static: method security altyapısı bu bean'i erken ister.
     */
    @Bean
    static RoleHierarchy roleHierarchy() {
        RoleHierarchyImpl.Builder builder = RoleHierarchyImpl.withDefaultRolePrefix();
        for (Role role : Role.values()) {
            if (!role.implies().isEmpty()) {
                builder.role(role.name()).implies(role.implies().stream().map(Role::name).toArray(String[]::new));
            }
        }
        return builder.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * Login/refresh/logout isteklerinde Authorization başlığı yok sayılır. Böylece frontend süresi dolmuş
     * bir access token'ı yanlışlıkla gönderse bile refresh çalışmaya devam eder.
     */
    private static BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
        return (HttpServletRequest request) ->
                PUBLIC_AUTH_PATHS.contains(request.getRequestURI()) ? null : delegate.resolve(request);
    }

    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(JwtClaims.ROLE);
        authorities.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
