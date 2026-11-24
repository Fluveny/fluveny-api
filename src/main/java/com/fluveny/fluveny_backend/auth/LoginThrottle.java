package com.fluveny.fluveny_backend.auth;

import com.fluveny.fluveny_backend.auth.infrastructure.LoginThrottleStore;
import com.fluveny.fluveny_backend.auth.infrastructure.ThrottleEntry;
import com.fluveny.fluveny_backend.platform.security.OpaqueTokens;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Atraso progressivo do login (história 2.3, NFR6), em {@code login_throttle}: três contadores de
 * falha, por origem (IP), por conta + origem e por conta. A origem tem 20 falhas livres, porque uma
 * rede compartilhada (o laboratório de uma turma) soma os erros de digitação de todos; conta +
 * origem e conta têm 3. Depois das falhas livres {@code n}, a espera é de {@code 2^(falhas-n)} s a
 * partir da última falha, com teto de 15 min para a origem e para conta + origem e de 30 s para a
 * conta {@code [ASSUMPTION]}: o titular, vindo de outra origem, espera no máximo 30 s por causa de
 * terceiros. Não há bloqueio rígido.
 *
 * <p>Antes do fim da espera, a tentativa recebe o maior dos três prazos, sem conferir a senha e sem
 * contar falha. O sucesso zera conta + origem e conta, nunca a origem. Cada documento expira 1 h
 * depois da última falha.
 *
 * <p>As chaves são o sha256 de um prefixo fixo com o e-mail normalizado e a origem: nada de e-mail
 * nem IP em claro no banco. O IP é o {@code getRemoteAddr()} que o {@code ProxySecretFilter}
 * resolve; a origem é o IPv4 inteiro ou o prefixo /64 do IPv6 (quem tem um /64 troca de endereço à
 * vontade).
 */
@Service
public class LoginThrottle {

    /** Falhas sem espera da origem. */
    static final int ORIGIN_FREE_FAILURES = 20;

    /** Falhas sem espera de conta + origem e da conta. */
    static final int ACCOUNT_FREE_FAILURES = 3;

    static final Duration ORIGIN_CAP = Duration.ofMinutes(15);

    /** {@code [ASSUMPTION]} (decisão do Lucas, 20/11). */
    static final Duration ACCOUNT_CAP = Duration.ofSeconds(30);

    /** Quanto o documento vive depois da última falha. */
    static final Duration RETENTION = Duration.ofHours(1);

    private final LoginThrottleStore store;

    private final Clock clock;

    LoginThrottle(LoginThrottleStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    /**
     * Os segundos que faltam para a próxima tentativa (o maior dos três prazos); zero se pode
     * tentar agora.
     *
     * @param email o e-mail normalizado
     * @param ip o IP da origem
     */
    public long retryAfterSeconds(String email, String ip) {
        Keys keys = Keys.of(email, ip);
        Map<String, Rule> rules =
                Map.of(
                        keys.origin,
                        new Rule(ORIGIN_FREE_FAILURES, ORIGIN_CAP),
                        keys.accountOrigin,
                        new Rule(ACCOUNT_FREE_FAILURES, ORIGIN_CAP),
                        keys.account,
                        new Rule(ACCOUNT_FREE_FAILURES, ACCOUNT_CAP));
        Instant now = now();
        long retryAfter = 0;
        for (ThrottleEntry entry : this.store.find(keys.all())) {
            Rule rule = rules.get(entry.keyHash());
            if (rule == null || entry.expiresAt() == null || !entry.expiresAt().isAfter(now)) {
                continue;
            }
            Instant until =
                    entry.lastFailureAt().plus(wait(entry.failures(), rule.free(), rule.cap()));
            if (until.isAfter(now)) {
                long millis = Duration.between(now, until).toMillis();
                retryAfter = Math.max(retryAfter, Math.max(1, (millis + 999) / 1000));
            }
        }
        return retryAfter;
    }

    /** Conta a falha nas três chaves. */
    public void recordFailure(String email, String ip) {
        Keys keys = Keys.of(email, ip);
        Instant now = now();
        Instant expiresAt = now.plus(RETENTION);
        this.store.recordFailure(keys.origin, "ip", null, now, expiresAt);
        this.store.recordFailure(keys.accountOrigin, "account_ip", keys.account, now, expiresAt);
        this.store.recordFailure(keys.account, "account", keys.account, now, expiresAt);
    }

    /** O sucesso zera conta + origem e conta; a origem continua. */
    public void recordSuccess(String email, String ip) {
        Keys keys = Keys.of(email, ip);
        this.store.delete(List.of(keys.accountOrigin, keys.account));
    }

    /**
     * Apaga as chaves {@code account} e {@code account_ip} dos e-mails (a exclusão da conta,
     * história 2.10). A chave {@code ip} fica: ela não identifica a conta. Documentos de antes da
     * 2.10 sem o {@code accountKeyHash} somem pelo TTL de 1 h.
     *
     * @param emails os e-mails normalizados; nulos são ignorados
     */
    public void forget(String... emails) {
        if (emails == null) {
            return;
        }
        List<String> accounts =
                Arrays.stream(emails).filter(Objects::nonNull).map(Keys::account).toList();
        this.store.deleteByAccount(accounts);
    }

    /**
     * A espera depois de {@code failures} falhas: zero nas {@code free} primeiras, {@code
     * 2^(f-free)} s depois, até o teto.
     */
    static Duration wait(int failures, int free, Duration cap) {
        if (failures < free) {
            return Duration.ZERO;
        }
        int exponent = failures - free;
        if (exponent >= 30) {
            return cap;
        }
        Duration wait = Duration.ofSeconds(1L << exponent);
        return (wait.compareTo(cap) > 0) ? cap : wait;
    }

    /**
     * A origem do IP: o IPv4 como veio; o prefixo /64 do IPv6, do endereço canônico ({@link
     * InetAddress#ofLiteral}, que nunca resolve nome). O que não é IP literal fica como veio.
     */
    static String origin(String ip) {
        if (ip == null || ip.indexOf(':') < 0) {
            return String.valueOf(ip);
        }
        try {
            InetAddress address = InetAddress.ofLiteral(ip);
            if (address instanceof Inet6Address) {
                byte[] bytes = address.getAddress();
                return HexFormat.of().formatHex(bytes, 0, 8) + "::/64";
            }
            return address.getHostAddress();
        } catch (IllegalArgumentException ex) {
            return ip;
        }
    }

    private Instant now() {
        return this.clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    /** As falhas livres e o teto de um tipo de chave. */
    private record Rule(int free, Duration cap) {}

    /** As três chaves, já em hash. */
    record Keys(String origin, String accountOrigin, String account) {

        static Keys of(String email, String ip) {
            String origin = LoginThrottle.origin(ip);
            return new Keys(
                    hash("ip", origin), hash("account_ip", email + "\n" + origin), account(email));
        }

        static String account(String email) {
            return hash("account", email);
        }

        List<String> all() {
            return List.of(this.origin, this.accountOrigin, this.account);
        }

        private static String hash(String kind, String value) {
            return OpaqueTokens.hash("fluveny-login-throttle\n" + kind + "\n" + value);
        }

        /** Sem as chaves. */
        @Override
        public String toString() {
            return "Keys[***]";
        }
    }
}
