package com.xmonit.solar;


import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.CommandLinePropertySource;
import org.springframework.core.env.SimpleCommandLinePropertySource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@EnableCaching
@SpringBootApplication
//@EnableAutoConfiguration(exclude = {ErrorMvcAutoConfiguration.class})
public class App {

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

    @Autowired
    AppConfig cfg;

    @Bean
    public CacheManager cacheManager() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(){
            @Override
            protected Cache createConcurrentMapCache(final String name) {
                switch ( name.toLowerCase() ) {
                    case "sensors":
                        return new ConcurrentMapCache(name,
                                Caffeine.newBuilder().expireAfterWrite(30, TimeUnit.SECONDS).maximumSize(10).build().asMap(), false);
                    case "singleton":
                        System.out.println("creating singleton cache");
                        return new ConcurrentMapCache(name,
                                Caffeine.newBuilder().expireAfterWrite(60, TimeUnit.SECONDS).maximumSize(10).build().asMap(), false);
                    default:
                        return new ConcurrentMapCache(name,
                                Caffeine.newBuilder().expireAfterWrite(60, TimeUnit.SECONDS).maximumSize(100).build().asMap(), false);
                }
            }
        };
        return cacheManager;
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/arduino/**").allowedOrigins("http://localhost");
                registry.addMapping("/epever/**").allowedOrigins("http://localhost");
            }
        };
    }

    @Bean
    public CommandLineRunner commandLineRunner(ApplicationContext ctx) {

        return args -> {

            CommandLinePropertySource<?> propSrc = new SimpleCommandLinePropertySource(args);

            Optional.ofNullable(propSrc.getProperty("commPortRegEx")).ifPresent(str -> cfg.commPortRegEx = str);
            Optional.ofNullable(propSrc.getProperty("remoteHostRegEx")).ifPresent(str -> cfg.remoteHostRegEx = str);

            System.out.println(cfg);

        };
    }

}