package io.github.rbleuse.playground.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class CorsConfig : WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        // ng serve (:4200) and vite (:5173) dev servers; in the cluster demo nginx serves the build same-origin, so no CORS there.
        registry
            .addMapping("/**")
            .allowedOrigins("http://localhost:4200", "http://localhost:5173")
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
    }
}
