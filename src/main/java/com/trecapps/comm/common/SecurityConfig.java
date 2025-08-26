package com.trecapps.comm.common;

import com.trecapps.auth.web.services.TrecAuthManagerWeb;
import com.trecapps.auth.web.services.TrecSecurityContextServlet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@EnableWebSecurity
@Configuration
@Order(2)
public class SecurityConfig {

    @Autowired
    SecurityConfig(TrecAuthManagerWeb trecAuthManagerWeb1, TrecSecurityContextServlet trecSecurityContext1)
    {
        trecAuthManagerWeb = trecAuthManagerWeb1;
        trecSecurityContext = trecSecurityContext1;
    }
    TrecAuthManagerWeb trecAuthManagerWeb;
    TrecSecurityContextServlet trecSecurityContext;

    @Bean
    protected SecurityFilterChain configure(HttpSecurity security) throws Exception
    {
        security = security.csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests((req) ->
                        req
                                .requestMatchers("/Notifications/**", "/Notifications/*", "/Messages/**", "/Messages/*")
                                .authenticated()
                                .anyRequest()
                                .permitAll()
                )
                .authenticationManager(trecAuthManagerWeb)
                .securityContext((cust)->
                        cust.securityContextRepository(trecSecurityContext)
                )
                .sessionManagement((cust)-> cust.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return security.build();
    }
}
