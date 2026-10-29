package com.notastrinitario.app.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@SuppressWarnings("unused")
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Solo redirige la ruta raíz a index.html
        registry.addViewController("/").setViewName("forward:/index.html");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Configuración para archivos estáticos
        registry.addResourceHandler(
                "/**",
                "/webjars/**",
                "/img/**",
                "/css/**",
                "/js/**")
                .addResourceLocations(
                        "classpath:/static/",
                        "classpath:/META-INF/resources/static/",
                        "classpath:/META-INF/resources/")
                .setCacheControl(CacheControl.noCache().cachePrivate());

        // Swagger UI
        registry.addResourceHandler("/swagger-ui/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/springfox-swagger-ui/");

        // Profile pictures
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:uploads/")
                .setCacheControl(CacheControl.noCache().cachePrivate());

        // Firmas director
        registry.addResourceHandler("/Firmas/**")
                .addResourceLocations("file:./Frontend/Firmas/")
                .setCacheControl(CacheControl.noCache().cachePrivate());
    }

    /**
     * Respuestas en streaming (StreamingResponseBody), como /api/ai/study-plan-stream
     * que reenvia el stream de la IA. Sin esto Spring MVC usa el timeout por
     * defecto del servidor (~30 s) y corta la respuesta a la mitad: el navegador
     * mostraba net::ERR_INCOMPLETE_CHUNKED_ENCODING y el plan nunca terminaba.
     * Tambien se usa un pool de hilos propio en vez del SimpleAsyncTaskExecutor
     * por defecto (que crea un hilo nuevo por peticion sin limite).
     */
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("mvc-async-");
        executor.initialize();
        configurer.setTaskExecutor(executor);
        // 10 minutos: de sobra para generar un plan (la IA se corta sola a los 60 s sin datos).
        configurer.setDefaultTimeout(10 * 60 * 1000L);
    }

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        // Configuración de coincidencia de rutas
    }
}