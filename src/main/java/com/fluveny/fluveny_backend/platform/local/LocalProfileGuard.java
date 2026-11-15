package com.fluveny.fluveny_backend.platform.local;

import com.mongodb.ConnectionString;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * O perfil {@code local} só roda na máquina do dev (AD-18). A API não sobe se ele estiver ativo
 * junto com {@code prod}, nem se o Mongo configurado não for local: {@code mongodb+srv://} é
 * recusado, e o host precisa ser {@code localhost}, {@code 127.0.0.1}, {@code ::1} ou {@code mongo}
 * (o serviço do compose do App). Sem URI, o Mongo vem do Testcontainers ({@code mise run up}).
 *
 * <p>Roda antes de qualquer bean ser criado e antes do {@code ProdEnvironmentGuard}, para a falha
 * citar os perfis e não uma variável que falta.
 */
@Component
@Profile("local")
class LocalProfileGuard implements BeanFactoryPostProcessor, EnvironmentAware, PriorityOrdered {

    static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "mongo");

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        check(this.environment);
    }

    static void check(Environment environment) {
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException(
                    "Os perfis local e prod não podem ficar ativos juntos (ativos: "
                            + String.join(",", environment.getActiveProfiles())
                            + "). O perfil local é só para a máquina do dev.");
        }
        String uri = environment.getProperty("spring.mongodb.uri");
        if (StringUtils.hasText(uri)) {
            checkUri(uri.strip());
        }
        String host = environment.getProperty("spring.mongodb.host");
        if (StringUtils.hasText(host)) {
            checkHost(host.strip());
        }
    }

    private static void checkUri(String uri) {
        if (uri.toLowerCase(Locale.ROOT).startsWith("mongodb+srv:")) {
            throw new IllegalStateException(
                    "Perfil local com um Mongo remoto (mongodb+srv://"
                            + srvHost(uri)
                            + "). O perfil local só aceita um Mongo local: "
                            + String.join(", ", sortedHosts())
                            + ".");
        }
        List<String> hosts = new ConnectionString(uri).getHosts();
        hosts.stream().map(LocalProfileGuard::hostOf).forEach(LocalProfileGuard::checkHost);
    }

    private static void checkHost(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        if (!LOCAL_HOSTS.contains(normalized)) {
            throw new IllegalStateException(
                    "Perfil local com um Mongo que não é local (host "
                            + host
                            + "). Hosts aceitos: "
                            + String.join(", ", sortedHosts())
                            + ".");
        }
    }

    /** {@code host:porta}, {@code [::1]:porta} ou só o host, sem a porta. */
    static String hostOf(String hostAndPort) {
        if (hostAndPort.startsWith("[")) {
            int end = hostAndPort.indexOf(']');
            return end < 0 ? hostAndPort : hostAndPort.substring(0, end + 1);
        }
        int colon = hostAndPort.lastIndexOf(':');
        boolean singleColon = colon >= 0 && hostAndPort.indexOf(':') == colon;
        return singleColon ? hostAndPort.substring(0, colon) : hostAndPort;
    }

    /** O host de uma URI {@code mongodb+srv://}, sem usuário, senha nem caminho. */
    private static String srvHost(String uri) {
        String rest = uri.substring(uri.indexOf("://") + 3);
        int at = rest.lastIndexOf('@');
        if (at >= 0) {
            rest = rest.substring(at + 1);
        }
        int end = rest.length();
        for (char separator : new char[] {'/', '?'}) {
            int index = rest.indexOf(separator);
            if (index >= 0 && index < end) {
                end = index;
            }
        }
        return rest.substring(0, end);
    }

    private static List<String> sortedHosts() {
        return LOCAL_HOSTS.stream().sorted().toList();
    }
}
