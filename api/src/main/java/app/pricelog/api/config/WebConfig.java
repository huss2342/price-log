package app.pricelog.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public WebConfig(org.springframework.core.env.Environment environment) {
        // The PWA is served from a different origin than the API, so the front
        // end's origin has to be listed explicitly.
        this.allowedOrigins = environment
                .getProperty("pricelog.auth.allowed-origins", String[].class,
                        new String[]{"http://localhost:4200"});
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
