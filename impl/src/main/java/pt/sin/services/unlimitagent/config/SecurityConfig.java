package pt.sin.services.unlimitagent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import pt.sin.services.unlimitagent.model.ErrorResponse;

import java.io.IOException;

import static org.springframework.http.HttpMethod.POST;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Value("${security.enabled:false}")
    private boolean securityEnabled;

    @Value("${security.jwt.roles-claim:roles}")
    private String rolesClaim;

    @Value("${JWT_ISSUER_URI:}")
    private String jwtIssuerUri;

    @Value("${JWT_JWK_SET_URI:}")
    private String jwtJwkSetUri;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (securityEnabled) {
            http.authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                            "/actuator/health",
                            "/swagger-ui/**", "/swagger-ui.html",
                            "/v3/api-docs/**"
                        ).permitAll()
                        .requestMatchers(POST, "/api/v1/incidents/analyze").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(POST, "/api/v1/admin/incidents/knowledge").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter()))
                    .authenticationEntryPoint((req, res, ex) ->
                        writeError(res, objectMapper, 401, "Unauthorized", ex.getMessage()))
                    .accessDeniedHandler((req, res, ex) ->
                        writeError(res, objectMapper, 403, "Forbidden", ex.getMessage()))
                );
        } else {
            log.warn("Security is DISABLED — all endpoints are accessible without authentication");
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        }

        return http.build();
    }

    private JwtAuthenticationConverter jwtAuthConverter() {
        JwtGrantedAuthoritiesConverter grantedConverter = new JwtGrantedAuthoritiesConverter();
        grantedConverter.setAuthoritiesClaimName(rolesClaim);
        grantedConverter.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(grantedConverter);
        return converter;
    }

    @Bean
    @ConditionalOnProperty(name = "security.enabled", havingValue = "true")
    @ConditionalOnMissingBean(JwtDecoder.class)
    public JwtDecoder jwtDecoder() {
        if (!jwtIssuerUri.isBlank()) {
            return JwtDecoders.fromIssuerLocation(jwtIssuerUri);
        }
        if (!jwtJwkSetUri.isBlank()) {
            return NimbusJwtDecoder.withJwkSetUri(jwtJwkSetUri).build();
        }
        throw new IllegalStateException(
                "security.enabled=true but neither JWT_ISSUER_URI nor JWT_JWK_SET_URI is configured");
    }

    private static void writeError(HttpServletResponse response, ObjectMapper objectMapper,
                                   int status, String error, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(),
            new ErrorResponse().error(error).detail(detail).status(status));
    }
}
