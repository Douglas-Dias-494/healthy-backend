package com.healthy.common;

import java.util.Locale;

/** "  Maria@Exemplo.COM " e "maria@exemplo.com" são a mesma conta: sempre se compara o e-mail normalizado. */
public final class Emails {

    private Emails() {
    }

    public static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
