package com.commodityledger.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless HTTP Basic authentication with two roles:
 * <ul>
 *   <li>TRADER — users, wallets, trades, portfolio, user ledger history;</li>
 *   <li>ADMIN  — everything a TRADER can do, plus price updates and the ledger audit.</li>
 * </ul>
 * Credentials come from application.yml (overridable with environment variables).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)   // stateless REST API, no browser session cookies
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/error", "/actuator/health", "/actuator/health/**", "/actuator/info",
                                "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/commodities/**").hasRole("ADMIN")
                        .requestMatchers("/api/ledger/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").hasAnyRole("TRADER", "ADMIN")
                        .anyRequest().denyAll())
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();   // bcrypt by default
    }

    @Bean
    public UserDetailsService userDetailsService(
            PasswordEncoder passwordEncoder,
            @Value("${app.security.trader.username}") String traderUsername,
            @Value("${app.security.trader.password}") String traderPassword,
            @Value("${app.security.admin.username}") String adminUsername,
            @Value("${app.security.admin.password}") String adminPassword) {

        UserDetails trader = org.springframework.security.core.userdetails.User
                .withUsername(traderUsername)
                .password(passwordEncoder.encode(traderPassword))
                .roles("TRADER")
                .build();
        UserDetails admin = org.springframework.security.core.userdetails.User
                .withUsername(adminUsername)
                .password(passwordEncoder.encode(adminPassword))
                .roles("ADMIN", "TRADER")
                .build();
        return new InMemoryUserDetailsManager(trader, admin);
    }
}
