package com.proyecto.parking.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Content-Security-Policy.
     *
     * <p>Las plantillas todavía usan atributos {@code style="..."} y manejadores
     * {@code onclick="..."} en línea, de ahí los {@code unsafe-inline}. Moverlos
     * a archivos .css/.js permitiría quitar ambos. Aun así la política bloquea
     * orígenes externos, plugins y la reescritura de {@code <base>}, y limita los
     * iframes al dashboard de Power BI.</p>
     */
    private static final String CSP = String.join("; ",
            "default-src 'self'",
            "script-src 'self' 'unsafe-inline'",
            "style-src 'self' 'unsafe-inline'",
            "img-src 'self' data: https:",
            "font-src 'self'",
            "connect-src 'self'",
            "frame-src https://app.powerbi.com",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'self'");

    private final LoginSuccessHandler loginSuccessHandler;
    private final LoginFailureHandler loginFailureHandler;
    private final EstadoCuentaFilter estadoCuentaFilter;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtCookieAuthenticationFilter jwtCookieAuthenticationFilter;

    public SecurityConfig(LoginSuccessHandler loginSuccessHandler,
                          LoginFailureHandler loginFailureHandler,
                          EstadoCuentaFilter estadoCuentaFilter,
                          JwtAuthenticationFilter jwtAuthenticationFilter,
                          JwtCookieAuthenticationFilter jwtCookieAuthenticationFilter) {
        this.loginSuccessHandler = loginSuccessHandler;
        this.loginFailureHandler = loginFailureHandler;
        this.estadoCuentaFilter = estadoCuentaFilter;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.jwtCookieAuthenticationFilter = jwtCookieAuthenticationFilter;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    // Evita que Spring Boot registre los filtros JWT como filtros de servlet
    // globales; cada uno solo debe correr dentro de su propia cadena.
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<JwtCookieAuthenticationFilter> jwtCookieFilterRegistration(
            JwtCookieAuthenticationFilter filter) {
        FilterRegistrationBean<JwtCookieAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * API stateless: usada hoy por el login/registro por JWT y sera la base de
     * autenticacion de cualquier cliente futuro (frontend separado, movil). No
     * comparte sesion ni CSRF con el resto de la aplicacion.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(ex -> ex
                // setStatus (no sendError): sendError dispara el forward de Tomcat/Spring Boot a /error,
                // y esa peticion interna cae en la cadena web (no matchea /api/**), devolviendo un
                // 302 a /login en vez del 401/403 real. setStatus evita ese forward.
                .authenticationEntryPoint((request, response, e) -> response.setStatus(401))
                .accessDeniedHandler((request, response, e) -> response.setStatus(403))
            );

        return http.build();
    }

    /**
     * Aplicacion web (Thymeleaf): la identidad viaja en un JWT guardado en una
     * cookie HttpOnly (ver JwtCookieAuthenticationFilter y LoginSuccessHandler),
     * no en sesion de servidor. Por eso queda STATELESS igual que la cadena de
     * la API, aunque siga usando formLogin/logout con paginas HTML.
     *
     * <p>Al no haber sesion, se pierde "maximumSessions(1)" (forzar una sola
     * sesion activa por cuenta): un JWT no tiene forma de invalidarse a
     * distancia sin volver a agregar estado en el servidor, que es justo lo
     * que se quería eliminar. Es un tradeoff aceptado, no un olvido.</p>
     */
    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                        "/", "/login", "/registro/cliente",
                        "/buscar", "/css/**", "/js/**", "/images/**", "/fonts/**", "/favicon.ico", "/favicon.svg",
                        "/error", "/error/**",
                        "/actuator/health").permitAll()
                .requestMatchers("/superadmin/**").hasRole("SUPERADMIN")
                .requestMatchers("/admin/**").hasRole("ADMINISTRADOR")
                // /comentario/** faltaba aquí: caía en anyRequest() y cualquier
                // usuario autenticado, incluido un admin, podía publicar.
                .requestMatchers("/cliente/**", "/reserva/**", "/zona/**", "/comentario/**").hasRole("CLIENTE")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .usernameParameter("email")
                .passwordParameter("password")
                .successHandler(loginSuccessHandler)
                .failureHandler(loginFailureHandler)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .clearAuthentication(true)
                .deleteCookies(JwtService.NOMBRE_COOKIE)
                .permitAll()
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex.accessDeniedPage("/error/403"))
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives(CSP))
                .frameOptions(frame -> frame.sameOrigin())
                .referrerPolicy(referrer -> referrer.policy(
                        ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                .httpStrictTransportSecurity(hsts -> hsts
                        .includeSubDomains(true)
                        .maxAgeInSeconds(31_536_000))
                .permissionsPolicy(pp -> pp.policy("geolocation=(), microphone=(), camera=()"))
            )
            // El token CSRF ya no puede vivir en sesion (no hay); se guarda en su
            // propia cookie (legible por JS a proposito, es lo que exige el patron
            // "double submit cookie" para que Thymeleaf/JS puedan leerlo y mandarlo).
            .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
            .addFilterBefore(jwtCookieAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(estadoCuentaFilter, JwtCookieAuthenticationFilter.class);

        return http.build();
    }
}
