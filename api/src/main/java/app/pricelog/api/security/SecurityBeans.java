package app.pricelog.api.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Password hashing. Strength 10 is the BCrypt default: slow enough to hurt a
 * guessing attack, fast enough that a login never feels it. Deliberately not
 * the full Spring Security filter chain -- this app authenticates with two
 * small custom filters instead.
 */
@Configuration
public class SecurityBeans {

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
