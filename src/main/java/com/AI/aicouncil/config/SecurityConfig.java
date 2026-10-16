package com.AI.aicouncil.config;

import com.AI.aicouncil.security.FirebaseAuthenticationFilter;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Stateless Firebase-bearer-token security.
 *
 * <p>{@code @EnableMethodSecurity} is what makes the {@code @PreAuthorize}
 * annotations on the controllers actually run — without it they are silently
 * ignored.</p>
 *
 * <p>Every route is denied unless listed here, so a new endpoint is private
 * until someone deliberately opens it.</p>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /** Comma-separated list, e.g. "https://ai-council-one-livid.vercel.app,http://localhost:5173". */
    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            FirebaseAuthenticationFilter firebaseFilter
    ) throws Exception {

        http
                .cors(Customizer.withDefaults())
                // Bearer tokens in a header, no cookies: CSRF does not apply.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Let Spring render real error statuses instead of masking them as 401/403.
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.ASYNC).permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/health").permitAll()
                        .requestMatchers("/api/auth/**", "/api/council/**").authenticated()
                        .anyRequest().denyAll()
                )
                // Missing/absent token -> 401 (not the default 403), so the client knows to sign in again.
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(firebaseFilter, UsernamePasswordAuthenticationFilter.class)
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(form -> form.disable());

        return http.build();
    }

    /** The single CORS policy for the whole API (controllers no longer carry @CrossOrigin). */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins.stream().map(String::trim).filter(s -> !s.isEmpty()).toList());
        config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        // The frontend reads Retry-After on 429s; browsers hide it cross-origin unless exposed.
        config.setExposedHeaders(List.of("Retry-After"));
        // Auth is a bearer header, not a cookie, so credentialed CORS is unnecessary.
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
