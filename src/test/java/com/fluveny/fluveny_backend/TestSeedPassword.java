package com.fluveny.fluveny_backend;

import com.fluveny.fluveny_backend.account.PasswordPolicy;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.junit.jupiter.api.extension.Extension;

/**
 * Senha dos Alunos de teste do carregador local de {@code account} (história 2.1) nos testes do
 * perfil {@code local}: gerada na execução, nunca fixa no código, e posta nas propriedades da JVM
 * quando a máquina não tem {@code LOCAL_SEED_PASSWORD} ou a tem vazia ou fora da regra de senha.
 * Como a {@link TestReceiptKey}: registrada pelo {@code ServiceLoader}.
 */
public final class TestSeedPassword implements Extension {

    public static final String VARIABLE = "LOCAL_SEED_PASSWORD";

    static {
        // Uma senha da máquina vazia ou fora da regra faria o carregador pular os Alunos.
        if (PasswordPolicy.violation(value()).isPresent()) {
            System.setProperty(VARIABLE, generate());
        }
    }

    /** Segue a regra de senha: letra, número e 26 caracteres. */
    public static String generate() {
        byte[] bytes = new byte[12];
        new SecureRandom().nextBytes(bytes);
        return "a1" + HexFormat.of().formatHex(bytes);
    }

    /** A senha em uso: a propriedade da JVM vence a variável da máquina, como no Spring. */
    public static String value() {
        String fromProperty = System.getProperty(VARIABLE);
        return (fromProperty != null) ? fromProperty : System.getenv(VARIABLE);
    }
}
