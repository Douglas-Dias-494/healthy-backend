package com.healthy.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.time.Clock;

@Configuration
@EnableAsync // permite @Async: o envio de e-mail não faz o usuário esperar (nem revela, pelo tempo, se a conta existe)
public class AppConfig {

    /** Relógio injetável: o código pede "agora" a ele, e os testes podem trocá-lo por um relógio fixo. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
