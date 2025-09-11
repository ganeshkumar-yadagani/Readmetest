package com.tmobile.publisher.azurestorage.security;

import com.tmobile.security.tapp.jwt.validator.JwtValidator;
import com.tmobile.security.tapp.jwt.validator.exception.JwtDecoderException;
import com.tmobile.security.tapp.jwt.validator.exception.JwtExpiredException;
import com.tmobile.security.tapp.jwt.validator.exception.JwtValidatorException;
import com.tmobile.security.tapp.jwt.validator.model.TappJwt;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtValidator jwtValidator;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws IOException, ServletException {

        String authType = request.getHeader("authtype");
        String ntid = request.getHeader("NTID");
        String token = extractBearerToken(request);

        // Validate authtype
        if (!StringUtils.equalsIgnoreCase(authType, "jwt")) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Invalid authtype. Expected 'jwt'\"}");
            return;
        }

        try {
            // Validate token using internal JwtValidator
            TappJwt tappJwt = jwtValidator.decodeAccessToken(token);
            validateClaims(tappJwt, ntid);

            // Build Spring Security authentication
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    ntid, null, Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (JwtDecoderException e) {
            handleFailure(response, "Access token is not a valid JWT", e, HttpStatus.UNAUTHORIZED);
            return;
        } catch (JwtExpiredException e) {
            handleFailure(response, "Access token expired", e, HttpStatus.UNAUTHORIZED);
            return;
        } catch (JwtValidatorException e) {
            handleFailure(response, "Access token invalid", e, HttpStatus.UNAUTHORIZED);
            return;
        } catch (JwtTokenValidationException e) {
            handleFailure(response, e.getMessage(), e, e.getHttpStatus());
            return;
        } catch (Exception e) {
            handleFailure(response, "Unexpected error during token validation", e, HttpStatus.INTERNAL_SERVER_ERROR);
            return;
        }

        chain.doFilter(request, response);
    }

    private void validateClaims(TappJwt tappJwt, String headerNtid) {
        if (tappJwt == null || tappJwt.getClaims() == null) {
            throw new JwtTokenValidationException("JWT claims missing", HttpStatus.UNAUTHORIZED);
        }
        Map<String, Object> claims = tappJwt.getClaims();
        Object ntidClaim = claims.get("ntid");
        if (ntidClaim == null || !StringUtils.equalsIgnoreCase(ntidClaim.toString(), headerNtid)) {
            throw new JwtTokenValidationException("NTID mismatch", HttpStatus.UNAUTHORIZED);
        }
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.isNotBlank(header) && header.toLowerCase().startsWith("bearer ")) {
            return header.substring(7).trim();
        }
        throw new JwtTokenValidationException("Missing or invalid Authorization header", HttpStatus.UNAUTHORIZED);
    }

    private void handleFailure(HttpServletResponse response, String message, Exception ex, HttpStatus status) throws IOException {
        log.error("Authentication failed: {}", message, ex);
        SecurityContextHolder.clearContext();
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.getWriter().write(
                String.format("{\"status\":%d,\"error\":\"%s\",\"message\":\"%s\"}",
                        status.value(), status.getReasonPhrase(), message));
    }
}



package com.tmobile.publisher.azurestorage.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        log.error("Unauthorized request to {}?{}", request.getRequestURI(), request.getQueryString(), authException);

        Map<String, Object> error = new HashMap<>();
        error.put("status", HttpServletResponse.SC_UNAUTHORIZED);
        error.put("error", "Unauthorized");
        error.put("message", authException.getMessage());
        error.put("path", request.getRequestURI());
        error.put("timestamp", Instant.now().toString());

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        mapper.writeValue(response.getWriter(), error);
    }
}


package com.tmobile.publisher.azurestorage.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;

public class JwtTokenValidationException extends AuthenticationException {

    private final HttpStatus httpStatus;

    public JwtTokenValidationException(String message, HttpStatus status) {
        super(message);
        this.httpStatus = status;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}


package com.tmobile.publisher.azurestorage.config;

import com.tmobile.publisher.azurestorage.security.CustomAuthenticationEntryPoint;
import com.tmobile.publisher.azurestorage.security.JwtAuthenticationFilter;
import com.tmobile.security.tapp.jwt.validator.JwtValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class WebSecurityConfiguration {

    private final JwtValidator jwtValidator;

    public WebSecurityConfiguration(JwtValidator jwtValidator) {
        this.jwtValidator = jwtValidator;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .headers(headers -> headers
                .contentSecurityPolicy("default-src 'self'")
                .xssProtection(xss -> xss.block(true))
                .frameOptions(frame -> frame.sameOrigin())
                .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/actuator/**").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(new JwtAuthenticationFilter(jwtValidator), UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(ex -> ex.authenticationEntryPoint(new CustomAuthenticationEntryPoint()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("https://my-ui.com", "https://admin.my-ui.com")); // 👈 whitelist your UI domains
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "NTID", "authtype"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(1800L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
