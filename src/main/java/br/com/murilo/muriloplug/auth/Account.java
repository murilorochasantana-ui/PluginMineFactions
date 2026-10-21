package br.com.murilo.muriloplug.auth;

import java.util.UUID;

/** Conta registrada. Guarda só o hash da senha (nunca a senha), com o algoritmo, as iterações e o salt usados. */
public record Account(UUID id, String nick, String algorithm, int iterations, String salt, String hash,
                      long registeredAt, String lastIp, long lastLogin) {

    public Account withLogin(String ip, long time) {
        return new Account(id, nick, algorithm, iterations, salt, hash, registeredAt, ip, time);
    }

    public Account withPassword(PasswordHasher.Hashed hashed) {
        return new Account(id, nick, hashed.algorithm(), hashed.iterations(), hashed.salt(), hashed.hash(),
                registeredAt, lastIp, lastLogin);
    }
}
