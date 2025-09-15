package com.tmobile.publisher.azurestorage.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Main security configuration for the Azure Storage Publisher service.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class WebSecurityConfiguration {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;

    @Bean
    @Order(1)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable default security features
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)

            // Stateless session
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Exception handling
            .exceptionHandling(ex -> ex.authenticationEntryPoint(customAuthenticationEntryPoint))

            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/swagger-ui/**",
                    "/v3/api-docs/**",
                    "/actuator/**"
                ).permitAll()
                .anyRequest().authenticated()
            )

            // Add custom JWT filter
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(List.of("http://localhost:8080"));
        cfg.setAllowedMethods(List.of("GET", "PUT", "POST", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "NTID", "authType"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(1800L); // 30 min preflight cache
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}


package com.tmobile.publisher.azurestorage.security;

import com.tmobile.publisher.azurestorage.security.exception.JwtTokenValidationException;
import com.tmobile.security.taap.jwt.validator.JwtValidator;
import com.tmobile.security.taap.jwt.validator.exception.JwtDecoderException;
import com.tmobile.security.taap.jwt.validator.exception.JwtExpiredException;
import com.tmobile.security.taap.jwt.validator.exception.JwtValidatorException;
import com.tmobile.security.taap.jwt.validator.model.TaapJwt;
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

/**
 * Custom JWT authentication filter.
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtValidator jwtValidator;
    private final String[] expectedAudiences;

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String HEADER_NTID = "NTID";
    private static final String HEADER_AUTH_TYPE = "authType";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = extractBearerToken(request);
            String ntidHeader = request.getHeader(HEADER_NTID);
            String authType = request.getHeader(HEADER_AUTH_TYPE);

            if (StringUtils.isBlank(token)) {
                log.debug("No Authorization header found, skipping filter");
                filterChain.doFilter(request, response);
                return;
            }

            if (!"azure".equalsIgnoreCase(authType)) {
                throw new JwtTokenValidationException("Invalid authType", HttpStatus.UNAUTHORIZED);
            }

            // Validate access token
            TaapJwt jwt = jwtValidator.decodeAccessToken(token);
            validateClaims(jwt, ntidHeader);

            // Build authentication object
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    ntidHeader, null, Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(authentication);

            log.info("JWT authentication successful for NTID={}", ntidHeader);

        } catch (JwtDecoderException ex) {
            handleFailure(response, "Access token is not a valid JWT", ex, HttpStatus.UNAUTHORIZED);
            return;
        } catch (JwtExpiredException ex) {
            handleFailure(response, "Access token expired", ex, HttpStatus.UNAUTHORIZED);
            return;
        } catch (JwtValidatorException ex) {
            handleFailure(response, "Access token validation failed", ex, HttpStatus.UNAUTHORIZED);
            return;
        } catch (JwtTokenValidationException ex) {
            handleFailure(response, ex.getMessage(), ex, ex.getStatus());
            return;
        } catch (Exception ex) {
            handleFailure(response, "Unexpected error during token validation", ex, HttpStatus.INTERNAL_SERVER_ERROR);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(AUTH_HEADER);
        if (StringUtils.isNotBlank(header) &&
                header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return header.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }

    private void validateClaims(TaapJwt jwt, String ntidHeader) {
        if (jwt == null || jwt.getClaims() == null) {
            throw new JwtTokenValidationException("JWT claims not found", HttpStatus.UNAUTHORIZED);
        }
        Map<String, Object> claims = jwt.getClaims();
        Object ntidClaim = claims.get("ntid");
        if (ntidClaim == null || !StringUtils.equalsIgnoreCase(ntidClaim.toString(), ntidHeader)) {
            throw new JwtTokenValidationException("NTID mismatch", HttpStatus.UNAUTHORIZED);
        }
    }

    private void handleFailure(HttpServletResponse response, String message, Exception ex, HttpStatus status)
            throws IOException {
        log.error("Authentication failed: {}", message, ex);
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.contains("/swagger-ui")
                || path.contains("/v3/api-docs")
                || path.contains("/actuator");
    }
}


package com.tmobile.publisher.azurestorage.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Custom AuthenticationEntryPoint.
 * Ensures unauthorized requests return JSON instead of redirecting to login.
 */
@Slf4j
@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.error("Unauthorized request to {}: {}", request.getRequestURI(), authException.getMessage());

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");

        Map<String, Object> body = new HashMap<>();
        body.put("error", "Unauthorized");
        body.put("message", authException.getMessage());
        body.put("path", request.getRequestURI());

        response.getWriter().write(mapper.writeValueAsString(body));
    }
}








package com.tmobile.publisher.azurestorage.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;

/**
 * Exception class representing validation failures during JWT processing.
 * <p>
 * This extends {@link AuthenticationException} so that Spring Security
 * can handle it as part of its authentication flow. Each instance
 * carries an {@link HttpStatus} which allows mapping specific validation
 * errors (like expired token, NTID mismatch, etc.) to proper HTTP responses.
 */
public class JwtTokenValidationException extends AuthenticationException {

    private final HttpStatus httpStatus;

    /**
     * Constructs a new JwtTokenValidationException with the specified message
     * and HTTP status.
     *
     * @param message    the detail message
     * @param statusCode the HTTP status to return to the client
     */
    public JwtTokenValidationException(String message, HttpStatus statusCode) {
        super(message);
        this.httpStatus = statusCode;
    }

    /**
     * @return the associated HTTP status for this exception
     */
    public HttpStatus getHttpStatus() {
        return httpStatus;
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

/**
 * Custom entry point for handling authentication failures in Spring Security.
 * <p>
 * Instead of redirecting to a login page (default Spring behavior),
 * this implementation returns a structured JSON response with details
 * about the authentication error. This is essential for REST APIs
 * consumed by UIs or external services.
 */
@Slf4j
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Commences an authentication scheme.
     * <p>
     * Called when an unauthenticated client tries to access a protected resource.
     *
     * @param request       that resulted in an {@link AuthenticationException}
     * @param response      so that the user agent can begin authentication
     * @param authException that caused the invocation
     * @throws IOException if an input or output exception occurs
     */
    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        log.error("Unauthorized request: {} {}",
                request.getMethod(), request.getRequestURI(), authException);

        // Build error response body
        Map<String, Object> body = new HashMap<>();
        body.put("status", HttpServletResponse.SC_UNAUTHORIZED);
        body.put("error", "Unauthorized");
        body.put("message", authException.getMessage());
        body.put("path", request.getRequestURI());
        body.put("timestamp", Instant.now().toString());

        // Send JSON response
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        mapper.writeValue(response.getWriter(), body);
    }
}
package com.tmobile.publisher.azurestorage.security;

import com.tmobile.security.tapp.jwt.validator.JwtValidator;
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
import java.util.List;
import java.util.Map;

/**
 * Filter that performs JWT-based authentication for every request.
 * <p>
 * Responsibilities:
 * <ul>
 *     <li>Check that the request contains {@code authtype=okta} or URI contains "azureop"</li>
 *     <li>Extract the JWT from the {@code Authorization: Bearer ...} header</li>
 *     <li>Validate the JWT using {@link JwtValidator}</li>
 *     <li>Verify NTID header matches the JWT claim</li>
 *     <li>Validate JWT audience against a configured list</li>
 *     <li>Set the authentication in Spring's {@link SecurityContextHolder}</li>
 *     <li>Return structured JSON error responses on failure</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER_TYPE = "Bearer";

    private final JwtValidator jwtValidator;
    private final List<String> expectedAudiences;

    /**
     * Main filter logic executed once per request.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws IOException, ServletException {

        // Custom pre-check: enforce authtype=okta or URI containing "azureop"
        if (!StringUtils.equalsIgnoreCase(request.getHeader("authtype"), "okta")
                && !request.getRequestURI().contains("azureop")) {
            respondBadRequest(response, "pass the correct token as per the authtype");
            return;
        }

        final String ntid = request.getHeader("NTID");
        final String token;

        try {
            token = extractBearerToken(request);
        } catch (JwtTokenValidationException ex) {
            handleFailure(response, ex.getMessage(), ex, ex.getHttpStatus());
            return;
        }

        try {
            // Step 1: Validate JWT using internal validator
            TappJwt tappJwt = jwtValidator.decodeAccessToken(token);

            // Step 2: Validate NTID consistency
            validateNtid(tappJwt, ntid);

            // Step 3: Validate audience
            validateAudience(tappJwt);

            // Step 4: Populate Spring Security context
            Authentication auth = new UsernamePasswordAuthenticationToken(
                    ntid, null, Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(auth);

        } catch (JwtValidatorException e) {
            // Internal library throws generic JwtValidatorException; classify by message
            String msg = (e.getMessage() == null) ? "" : e.getMessage().toLowerCase();
            if (msg.contains("expired")) {
                handleFailure(response, "Access token expired", e, HttpStatus.UNAUTHORIZED);
            } else if (msg.contains("jwt") || msg.contains("decoder")) {
                handleFailure(response, "Access token is not a valid JWT", e, HttpStatus.UNAUTHORIZED);
            } else {
                handleFailure(response, "Access token invalid", e, HttpStatus.UNAUTHORIZED);
            }
            return;

        } catch (JwtTokenValidationException e) {
            handleFailure(response, e.getMessage(), e, e.getHttpStatus());
            return;

        } catch (Exception e) {
            handleFailure(response, "Unexpected error during token validation", e,
                    HttpStatus.INTERNAL_SERVER_ERROR);
            return;
        }

        // Continue chain if authentication succeeds
        chain.doFilter(request, response);
    }

    /**
     * Extracts the JWT from the Authorization header.
     *
     * @throws JwtTokenValidationException if header is missing or malformed
     */
    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION);
        if (StringUtils.isNotBlank(header)
                && header.regionMatches(true, 0, BEARER_TYPE, 0, BEARER_TYPE.length())) {
            return header.substring(BEARER_TYPE.length()).trim();
        }
        throw new JwtTokenValidationException("Missing or invalid Authorization header",
                HttpStatus.UNAUTHORIZED);
    }

    /**
     * Validates that the NTID claim in JWT matches the NTID request header.
     */
    private void validateNtid(TappJwt tappJwt, String headerNtid) {
        if (tappJwt == null || tappJwt.getClaims() == null) {
            throw new JwtTokenValidationException("JWT claims missing", HttpStatus.UNAUTHORIZED);
        }
        Map<String, Object> claims = tappJwt.getClaims();
        Object ntidClaim = claims.get("ntid");
        if (ntidClaim == null
                || !StringUtils.equalsIgnoreCase(ntidClaim.toString(), headerNtid)) {
            throw new JwtTokenValidationException("NTID mismatch", HttpStatus.UNAUTHORIZED);
        }
    }

    /**
     * Validates that the JWT audience matches one of the configured audiences.
     */
    private void validateAudience(TappJwt tappJwt) {
        if (expectedAudiences == null || expectedAudiences.isEmpty()) {
            return; // no audience check configured
        }
        Object audClaim = tappJwt.getClaims().get("aud");
        if (audClaim instanceof String aud) {
            if (!expectedAudiences.contains(aud)) {
                throw new JwtTokenValidationException("Invalid audience", HttpStatus.UNAUTHORIZED);
            }
        } else if (audClaim instanceof List<?> audList) {
            boolean ok = expectedAudiences.stream().anyMatch(audList::contains);
            if (!ok) {
                throw new JwtTokenValidationException("Invalid audience", HttpStatus.UNAUTHORIZED);
            }
        } else {
            throw new JwtTokenValidationException("Audience claim missing/invalid",
                    HttpStatus.UNAUTHORIZED);
        }
    }

    /**
     * Writes a structured JSON error response for authentication failures.
     */
    private void handleFailure(HttpServletResponse response,
                               String message,
                               Exception ex,
                               HttpStatus status) throws IOException {
        log.error("Authentication failed: {}", message, ex);
        SecurityContextHolder.clearContext();
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.getWriter().write(
                String.format("{\"status\":%d,\"error\":\"%s\",\"message\":\"%s\"}",
                        status.value(), status.getReasonPhrase(), message));
    }

    /**
     * Writes a 400 Bad Request error response.
     */
    private void respondBadRequest(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"" + msg + "\"}");
    }
}

package com.tmobile.publisher.azurestorage.config;

import com.tmobile.publisher.azurestorage.security.CustomAuthenticationEntryPoint;
import com.tmobile.publisher.azurestorage.security.JwtAuthenticationFilter;
import com.tmobile.security.tapp.jwt.validator.JwtValidator;
import org.springframework.beans.factory.annotation.Value;
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

/**
 * Main Spring Security configuration for the application.
 * <p>
 * Responsibilities:
 * <ul>
 *     <li>Register custom JWT filter ({@link JwtAuthenticationFilter})</li>
 *     <li>Configure CORS for frontend → backend requests</li>
 *     <li>Apply security headers (CSP, XSS, HSTS, FrameOptions)</li>
 *     <li>Permit certain endpoints (Swagger, actuator)</li>
 *     <li>Enforce stateless session management</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class WebSecurityConfiguration {

    private final JwtValidator jwtValidator;

    public WebSecurityConfiguration(JwtValidator jwtValidator) {
        this.jwtValidator = jwtValidator;
    }

    /** Expected audiences loaded from application properties */
    @Value("#{'${api.security.config.audid:}'.trim().isEmpty() ? null : '${api.security.config.audid:}'.trim().split('\\s*,\\s*')}")
    private List<String> expectedAudiences;

    /** Allowed CORS origins loaded from application properties */
    @Value("#{'${security.cors.allowed-origins:https://my-ui.com}'.trim().split('\\s*,\\s*')}")
    private List<String> corsAllowedOrigins;

    /**
     * Defines the Spring Security filter chain.
     *
     * @param http HttpSecurity builder
     * @return configured SecurityFilterChain
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'"))
                .xssProtection(xss -> xss.block(true))
                .frameOptions(frame -> frame.sameOrigin())
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains()   // Spring Security 6.x (parameterless)
                    .maxAgeInSeconds(31536000) // 1 year
                )
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html",
                        "/v3/api-docs/**", "/actuator/**").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() // CORS preflight
                .anyRequest().authenticated()
            )
            .addFilterBefore(new JwtAuthenticationFilter(jwtValidator, expectedAudiences),
                    UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(ex -> ex.authenticationEntryPoint(new CustomAuthenticationEntryPoint()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        return http.build();
    }

    /**
     * Defines the CORS configuration bean used by Spring Security.
     *
     * @return CorsConfigurationSource with allowed origins, methods, and headers
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(corsAllowedOrigins);
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "NTID", "authtype"));
        cfg.setExposedHeaders(List.of("Authorization"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(1800L); // 30 minutes preflight cache

        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }
}
package com.tmobile.publisher.azurestorage.config;

import com.tmobile.publisher.azurestorage.security.CustomAuthenticationEntryPoint;
import com.tmobile.publisher.azurestorage.security.JwtAuthenticationFilter;
import com.tmobile.security.tapp.jwt.validator.JwtValidator;
import org.springframework.beans.factory.annotation.Value;
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

    /** Expected audiences from application.yml */
    @Value("#{'${api.security.config.audid:}'.trim().isEmpty() ? null : '${api.security.config.audid:}'.trim().split('\\s*,\\s*')}")
    private List<String> expectedAudiences;

    /** Allowed CORS origins from application.yml */
    @Value("#{'${security.cors.allowed-origins:https://my-ui.com}'.trim().split('\\s*,\\s*')}")
    private List<String> corsAllowedOrigins;

    /** Non-secured endpoints from application.yml (ex: /health|GET,/swagger-ui/**|ALL) */
    @Value("#{'${security.non.secure.endpoints:/swagger-ui/**|ALL,/swagger-ui.html|ALL,/v3/api-docs/**|ALL,/actuator/**|ALL}'.split(',')}")
    private List<String> nonSecuredEndpoints;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'"))
                .xssProtection(xss -> xss.block(true))
                .frameOptions(frame -> frame.sameOrigin())
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains()
                    .maxAgeInSeconds(31536000)
                )
            )
            .authorizeHttpRequests(auth -> {
                // ✅ Register all non-secured endpoints
                for (String endpoint : nonSecuredEndpoints) {
                    if (endpoint.contains("|")) {
                        String[] parts = endpoint.split("\\|");
                        String path = parts[0].trim();
                        String method = parts[1].trim().toUpperCase();

                        if ("ALL".equals(method)) {
                            auth.requestMatchers(path).permitAll();
                        } else {
                            auth.requestMatchers(HttpMethod.valueOf(method), path).permitAll();
                        }
                    } else {
                        auth.requestMatchers(endpoint.trim()).permitAll();
                    }
                }

                // ✅ CORS preflight always allowed
                auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();

                // ✅ Everything else requires authentication
                auth.anyRequest().authenticated();
            })
            .addFilterBefore(new JwtAuthenticationFilter(jwtValidator, expectedAudiences),
                    UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(ex -> ex.authenticationEntryPoint(new CustomAuthenticationEntryPoint()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(corsAllowedOrigins);
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "NTID", "authtype"));
        cfg.setExposedHeaders(List.of("Authorization"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(1800L);

        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }
}
