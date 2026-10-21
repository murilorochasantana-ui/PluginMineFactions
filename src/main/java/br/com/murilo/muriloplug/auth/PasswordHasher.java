package br.com.murilo.muriloplug.auth;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Hash de senha com PBKDF2WithHmacSHA256 (Java puro), salt aleatório de 16 bytes por senha e 210.000 iterações.
 * O algoritmo, as iterações e o salt são guardados junto com o hash, para poder mudar os parâmetros no futuro.
 * Essas contas são pesadas de propósito: chame sempre FORA da thread principal.
 */
public final class PasswordHasher {

    public static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    public static final int ITERATIONS = 210_000;

    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Resultado de um hash, pronto para ser salvo (salt e hash em Base64). */
    public record Hashed(String algorithm, int iterations, String salt, String hash) {}

    private PasswordHasher() {
    }

    public static Hashed hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] derived = derive(password, salt, ITERATIONS, ALGORITHM, KEY_BITS);
        return new Hashed(ALGORITHM, ITERATIONS, Base64.getEncoder().encodeToString(salt),
                Base64.getEncoder().encodeToString(derived));
    }

    /** Confere a senha usando os parâmetros guardados na conta, comparando em tempo constante. */
    public static boolean verify(String password, Account account) {
        try {
            byte[] salt = Base64.getDecoder().decode(account.salt());
            byte[] expected = Base64.getDecoder().decode(account.hash());
            byte[] actual = derive(password, salt, account.iterations(), account.algorithm(), expected.length * 8);
            return MessageDigest.isEqual(expected, actual);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static byte[] derive(String password, byte[] salt, int iterations, String algorithm, int keyBits) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, keyBits);
        try {
            return SecretKeyFactory.getInstance(algorithm).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Algoritmo de senha indisponível: " + algorithm, e);
        } finally {
            spec.clearPassword();
        }
    }
}
