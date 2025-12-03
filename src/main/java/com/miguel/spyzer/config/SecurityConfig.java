package com.miguel.spyzer.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

        private final JwtAuthenticationFilter jwtAuthFilter;
        private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                http
                                .csrf(csrf -> csrf.disable())
                                .cors(cors -> cors.configure(http)) // Uses the CorsConfig bean
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers("/api/users/oauth").permitAll() // Allow
                                                                                                 // legacy/internal
                                                                                                 // endpoint if needed,
                                                                                                 // or
                                                                                                 // remove
                                                .requestMatchers("/error").permitAll()
                                                .anyRequest().authenticated())
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .oauth2Login(oauth2 -> oauth2
                                                .successHandler(oAuth2LoginSuccessHandler))
                                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }
}