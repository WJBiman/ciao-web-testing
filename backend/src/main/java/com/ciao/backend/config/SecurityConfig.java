package com.ciao.backend.config;

import com.ciao.backend.security.AuthTokenFilter;
import com.ciao.backend.security.UserDetailsServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.HttpMethod;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    UserDetailsServiceImpl userDetailsService;

    @Bean
    public AuthTokenFilter authenticationJwtTokenFilter() {
        return new AuthTokenFilter();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();

        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());

        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(org.springframework.security.config.Customizer.withDefaults())
                .exceptionHandling(errors -> errors.accessDeniedHandler((request, response, exception) -> {
                    response.setStatus(403);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"message\":\"Your account does not have permission for this action.\"}");
                }).authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(401);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"message\":\"Please sign in to continue.\"}");
                }))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> 
                    auth.requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/routes", "/api/routes/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/schedules", "/api/schedules/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/fleet/buses/active", "/api/fleet/branches").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/reservations/lock").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/reservations/ticket/**", "/api/reservations/schedules/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/payments/checkout").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/group-bookings").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/group-bookings/status").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/group-bookings/recover-token").permitAll()
                        .requestMatchers("/api/parcels/quote", "/api/parcels/quote/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/parcels", "/api/parcels/", "/api/parcels/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/parcels/track/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/lost-items").permitAll()
                        .requestMatchers("/api/test/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/eer/groups/pay-deposit", "/api/eer/groups/pay-balance").permitAll()
                        .requestMatchers("/api/eer/**").authenticated()
                        .anyRequest().authenticated()
                );
        
        http.authenticationProvider(authenticationProvider());

        http.addFilterBefore(authenticationJwtTokenFilter(), UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
}

