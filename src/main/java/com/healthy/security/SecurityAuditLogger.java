package com.healthy.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Registro de eventos de segurança, num logger próprio ("security"), para poder ser enviado a um arquivo
 * ou sistema de alertas. Regras: NUNCA registrar senha, token nem link de recuperação, e mascarar e-mails.
 * Serve para investigar ataques (muitas falhas de login do mesmo IP) e para auditoria.
 */
@Component
public class SecurityAuditLogger {

    private static final Logger log = LoggerFactory.getLogger("security");

    public void loginSuccess(Long userId, String ip) {
        log.info("event=LOGIN_SUCCESS userId={} ip={}", userId, ip);
    }

    public void loginFailure(String email, String ip) {
        log.warn("event=LOGIN_FAILURE email={} ip={}", maskEmail(email), ip);
    }

    public void rateLimited(String action, String ip) {
        log.warn("event=RATE_LIMITED action={} ip={}", action, ip);
    }

    public void accountCreated(Long userId, String ip) {
        log.info("event=ACCOUNT_CREATED userId={} ip={}", userId, ip);
    }

    public void passwordResetRequested(String email, String ip) {
        log.info("event=PASSWORD_RESET_REQUESTED email={} ip={}", maskEmail(email), ip);
    }

    public void passwordResetCompleted(Long userId, String ip) {
        log.info("event=PASSWORD_RESET_COMPLETED userId={} ip={}", userId, ip);
    }

    public void passwordResetFailed(String ip) {
        log.warn("event=PASSWORD_RESET_FAILED ip={}", ip);
    }

    public void passwordChanged(Long userId, String ip) {
        log.info("event=PASSWORD_CHANGED userId={} ip={}", userId, ip);
    }

    /** "maria@exemplo.com" vira "m***@exemplo.com": dá para correlacionar sem expor o endereço inteiro. */
    static String maskEmail(String email) {
        int at = email == null ? -1 : email.indexOf('@');
        if (at < 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
