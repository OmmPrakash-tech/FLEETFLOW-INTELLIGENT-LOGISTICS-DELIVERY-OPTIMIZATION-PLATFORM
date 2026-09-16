package com.example.backend.security;

import com.example.backend.common.Store;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;
import org.springframework.web.filter.OncePerRequestFilter;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwords() { return new BCryptPasswordEncoder(12); }
    @Bean JwtDecoder decoder(@Value("${fleetflow.jwt-secret}") String secret) {
        if (secret.length() < 32) throw new IllegalStateException("JWT_SECRET must contain at least 32 characters");
        var decoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256")).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("fleetflow"));
        return decoder;
    }
    @Bean SecurityFilterChain security(HttpSecurity http, JwtDecoder decoder, Store store, @Value("${fleetflow.cors-origin}") String origin) throws Exception {
        var cors = new CorsConfiguration(); cors.setAllowedOrigins(List.of(origin));
        cors.setAllowedMethods(List.of("GET","POST","PATCH","DELETE","OPTIONS")); cors.setAllowedHeaders(List.of("Authorization","Content-Type","Idempotency-Key"));
        var source = new UrlBasedCorsConfigurationSource(); source.registerCorsConfiguration("/**",cors);
        http.cors(c -> c.configurationSource(source)).csrf(c -> c.disable()).sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.authorizeHttpRequests(a -> a.requestMatchers("/api/auth/login","/api/auth/register","/api/auth/refresh","/api/auth/forgot-password","/api/auth/reset-password","/actuator/health/**","/v3/api-docs/**","/swagger-ui/**","/swagger-ui.html").permitAll()
            .requestMatchers("/actuator/**").hasRole("ADMIN").anyRequest().authenticated());
        http.exceptionHandling(e -> e.authenticationEntryPoint((r,s,x) -> reject(s,401,"UNAUTHORIZED")).accessDeniedHandler((r,s,x) -> reject(s,403,"FORBIDDEN")));
        http.addFilterBefore(new OncePerRequestFilter() {
            @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
                String header = req.getHeader("Authorization");
                if (header != null && header.startsWith("Bearer ")) {
                    try {
                        var jwt = decoder.decode(header.substring(7));
                        var rows = store.rows("SELECT id,role,active,force_reset,token_version FROM app_user WHERE id=?",UUID.fromString(jwt.getSubject()));
                        if (rows.isEmpty()) { reject(res,401,"UNAUTHORIZED"); return; }
                        var user = rows.getFirst();
                        if (!Boolean.TRUE.equals(user.get("active")) || Store.integer(user,"token_version") != ((Number)jwt.getClaim("version")).intValue()) { reject(res,401,"TOKEN_REVOKED"); return; }
                        if (Boolean.TRUE.equals(user.get("force_reset")) && !Set.of("/api/auth/password","/api/auth/me","/api/auth/logout").contains(req.getRequestURI())) { reject(res,403,"PASSWORD_RESET_REQUIRED"); return; }
                        String role = user.get("role").toString();
                        var auth = new UsernamePasswordAuthenticationToken(new Actor(Store.id(user,"id"),role),null,List.of(new SimpleGrantedAuthority("ROLE_"+role)));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    } catch (JwtException | IllegalArgumentException e) { reject(res,401,"INVALID_TOKEN"); return; }
                    catch (org.springframework.dao.DataAccessException e) { reject(res,503,"DATABASE_UNAVAILABLE"); return; }
                }
                chain.doFilter(req,res);
            }
        }, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
    static void reject(HttpServletResponse res, int status, String code) throws IOException {
        res.setStatus(status); res.setContentType("application/json");
        res.getWriter().write("{\"status\":"+status+",\"code\":\""+code+"\",\"message\":\""+code.replace('_',' ')+"\"}");
    }
}
