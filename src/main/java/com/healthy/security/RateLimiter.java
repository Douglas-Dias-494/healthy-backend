package com.healthy.security;

import com.healthy.common.TooManyRequestsException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limitador de tentativas por "janela deslizante", em memória.
 *
 * Funciona assim: cada chave (ex.: "login-email:maria@x.com") guarda os instantes dos eventos recentes.
 * {@link #requireBelow} recusa se, dentro da janela, já houve eventos demais; {@link #record} registra um.
 *
 * Limites desta versão: o estado vive na memória de UM servidor. Com vários servidores (ou após reiniciar)
 * cada um conta por conta própria; para isso um Redis seria o próximo passo.
 */
@Component
public class RateLimiter {

    /** Acima disto, entradas antigas são varridas para a memória não crescer sem fim. */
    private static final int CLEANUP_THRESHOLD = 10_000;
    private static final Duration KEEP_FOR = Duration.ofHours(2);

    private final Clock clock;
    private final Map<String, Deque<Instant>> events = new ConcurrentHashMap<>();

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Registra um evento (tentativa) para a chave. */
    public void record(String key) {
        Deque<Instant> deque = events.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            deque.addLast(clock.instant());
        }
        if (events.size() > CLEANUP_THRESHOLD) {
            cleanup();
        }
    }

    /**
     * Lança {@link TooManyRequestsException} se a chave já tiver {@code limit} eventos ou mais dentro de
     * {@code window}. Não registra nada: quem chama decide o que conta como tentativa.
     */
    public void requireBelow(String key, int limit, Duration window) {
        Deque<Instant> deque = events.get(key);
        if (deque == null) {
            return;
        }
        Instant now = clock.instant();
        synchronized (deque) {
            while (!deque.isEmpty() && !deque.peekFirst().isAfter(now.minus(window))) {
                deque.removeFirst(); // saiu da janela
            }
            if (deque.size() >= limit) {
                long retryAfter = Duration.between(now, deque.peekFirst().plus(window)).toSeconds() + 1;
                throw new TooManyRequestsException(Math.max(1, retryAfter));
            }
        }
    }

    /** Esquece os eventos da chave (ex.: login bem-sucedido zera as falhas daquele e-mail). */
    public void clear(String key) {
        events.remove(key);
    }

    /** Usado pelos testes para começar do zero. */
    public void clearAll() {
        events.clear();
    }

    private void cleanup() {
        Instant limit = clock.instant().minus(KEEP_FOR);
        events.entrySet().removeIf(entry -> {
            Deque<Instant> deque = entry.getValue();
            synchronized (deque) {
                return deque.isEmpty() || deque.peekLast().isBefore(limit);
            }
        });
    }
}
