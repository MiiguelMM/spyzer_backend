package com.miguel.spyzer;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
@EnableScheduling

public class SpyzerApplication {

	@Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    public static void main(String[] args) {
        // Cargar variables de entorno desde .env (solo en desarrollo)
        try {
            Dotenv dotenv = Dotenv.configure()
                    .ignoreIfMissing() // No falla si no existe .env (útil en Docker/producción)
                    .load();

            // Configurar las variables como propiedades del sistema
            dotenv.entries().forEach(entry ->
                System.setProperty(entry.getKey(), entry.getValue())
            );

            System.out.println("✅ Variables de entorno cargadas desde .env");
        } catch (Exception e) {
            System.out.println("⚠️  No se encontró archivo .env, usando variables del sistema");
        }

        SpringApplication.run(SpyzerApplication.class, args);
    }
}