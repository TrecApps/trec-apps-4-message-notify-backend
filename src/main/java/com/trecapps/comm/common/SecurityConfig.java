package com.trecapps.comm.common;

import com.trecapps.auth.webflux.services.TrecAuthManagerReactive;
import com.trecapps.auth.webflux.services.TrecSecurityContextReactive;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
@Slf4j
public class SecurityConfig {

    @Autowired
    SecurityConfig(
                   TrecSecurityContextReactive trecSecurityContext1,
                   TrecAuthManagerReactive trecAuthManagerReactive)
    {
        trecSecurityContext = trecSecurityContext1;
        this.trecAuthManagerReactive = trecAuthManagerReactive;
    }
    TrecSecurityContextReactive trecSecurityContext;
    TrecAuthManagerReactive trecAuthManagerReactive;

    String[] restrictedEndpoints = {
            "/Notifications/*",
            "/Notifications/**"

    };

    String[] verifiedEndpoints = {
            "/Messages/*",
            "/Messages/**",
            "/Conversations/**",
            "/Conversations/*"
    };

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http) {
        log.info("Preparing Security Bean");

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(restrictedEndpoints).authenticated()
                        .pathMatchers(verifiedEndpoints).hasAuthority("TREC_VERIFIED")
                        .anyExchange().permitAll())
                .authenticationManager(trecAuthManagerReactive)
                .securityContextRepository(trecSecurityContext)

                .build();
    }
}
