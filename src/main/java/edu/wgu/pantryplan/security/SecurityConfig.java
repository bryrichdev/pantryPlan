package edu.wgu.pantryplan.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.switchuser.SwitchUserFilter;
import org.springframework.security.web.authentication.switchuser.SwitchUserGrantedAuthority;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * Application security policy.
 *
 * <p>Every route except the public landing, registration, and login pages
 * requires an authenticated session. CSRF protection stays on the default
 * enabled setting, so every state-changing form carries a synchronizer token.
 *
 * <p>Administrators can view the app as another account. That uses Spring
 * Security's SwitchUserFilter: the admin's own login is kept inside the
 * switched session and restored on exit, so no password is ever needed or
 * shared, and every existing controller works unchanged because the principal
 * really is the other account for the duration.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    static final String IMPERSONATE_URL = "/admin/impersonate";
    static final String EXIT_IMPERSONATION_URL = "/admin/impersonate/exit";

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    /**
     * BCrypt with a work factor of 12. The algorithm is adaptive and salts each
     * hash individually, so identical passwords never produce identical rows.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   AppUserDetailsService userDetailsService) throws Exception {
        http
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                        .requestMatchers("/", "/register", "/login", "/error").permitAll()
                        /* Order matters: the exit rule must come before the
                           general admin rule. While viewing as someone else the
                           session holds that account's role, not ROLE_ADMIN. */
                        .requestMatchers(EXIT_IMPERSONATION_URL)
                                .hasAuthority(SwitchUserFilter.ROLE_PREVIOUS_ADMINISTRATOR)
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .defaultSuccessUrl("/dashboard", true)
                        .failureUrl("/login?error")
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll())
                .sessionManagement(session -> session
                        .sessionFixation(fixation -> fixation.migrateSession()))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; img-src 'self' data:; "
                                        + "object-src 'none'; frame-ancestors 'none'; "
                                        + "base-uri 'self'; form-action 'self'")))
                /* SwitchUserFilter sits after the authorization filter in the
                   chain, so the URL rules above run first. */
                .addFilter(switchUserFilter(userDetailsService));
        return http.build();
    }

    /**
     * Built here rather than declared as a bean on purpose. Spring Boot
     * registers every Filter bean as a servlet filter as well, which would run
     * it a second time outside the security chain.
     */
    private SwitchUserFilter switchUserFilter(AppUserDetailsService userDetailsService) {
        SwitchUserFilter filter = new SwitchUserFilter();
        filter.setUserDetailsService(nonAdminAccounts(userDetailsService));
        filter.setSwitchUserMatcher(PathPatternRequestMatcher.withDefaults()
                .matcher(HttpMethod.POST, IMPERSONATE_URL));
        filter.setExitUserMatcher(PathPatternRequestMatcher.withDefaults()
                .matcher(HttpMethod.POST, EXIT_IMPERSONATION_URL));
        filter.setSwitchFailureUrl("/admin/users?switchFailed");
        filter.setSuccessHandler(impersonationRedirects());
        filter.afterPropertiesSet();
        return filter;
    }

    /**
     * Only ordinary accounts can be viewed. Stepping into another admin would
     * hand over that admin's powers, which is exactly what impersonation must
     * never do. The form never offers it, but the check has to live here,
     * because a request can name any account.
     */
    private UserDetailsService nonAdminAccounts(AppUserDetailsService delegate) {
        return username -> {
            UserDetails target = delegate.loadUserByUsername(username);
            boolean isAdmin = target.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch("ROLE_ADMIN"::equals);
            if (isAdmin) {
                throw new DisabledException("Administrator accounts cannot be viewed as another user");
            }
            return target;
        };
    }

    /**
     * One handler serves both directions. A session that now carries the
     * switch authority has just started viewing as someone; one without it has
     * just returned. Both are logged, so there is a record of who looked at
     * which account.
     */
    private AuthenticationSuccessHandler impersonationRedirects() {
        return (request, response, authentication) -> {
            Authentication admin = sourceOf(authentication);
            if (admin != null) {
                log.info("Admin {} started viewing as {}", admin.getName(), authentication.getName());
                response.sendRedirect(request.getContextPath() + "/dashboard");
            } else {
                log.info("Admin {} returned to their own account", authentication.getName());
                response.sendRedirect(request.getContextPath() + "/admin/users");
            }
        };
    }

    /** The admin's own login, if this session is viewing as someone else. */
    static Authentication sourceOf(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (authority instanceof SwitchUserGrantedAuthority switched) {
                return switched.getSource();
            }
        }
        return null;
    }
}
