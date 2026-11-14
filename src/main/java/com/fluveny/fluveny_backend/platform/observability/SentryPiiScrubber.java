package com.fluveny.fluveny_backend.platform.observability;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import io.sentry.protocol.Request;

/**
 * Tira PII de todo evento antes de ir para o Sentry (NFR17): usuário, IP,
 * cookies, query string, cabeçalhos de autenticação e o corpo da requisição. Complementa o
 * {@code sentry.send-default-pii=false}.
 */
@Component
public class SentryPiiScrubber implements SentryOptions.BeforeSendCallback {

	static final Set<String> SENSITIVE_HEADERS = Set.of("authorization", "proxy-authorization", "cookie",
			"set-cookie", "x-api-key", "x-fluveny-client", "x-forwarded-for", "x-real-ip", "forwarded", "cf-connecting-ip",
			"true-client-ip", "x-client-ip", "x-original-forwarded-for");

	@Override
	public SentryEvent execute(SentryEvent event, Hint hint) {
		event.setUser(null);
		Request request = event.getRequest();
		if (request != null) {
			request.setCookies(null);
			// A query string pode levar token, e-mail ou termo de busca.
			request.setQueryString(null);
			request.setData(null);
			request.setBodySize(null);
			Map<String, String> headers = request.getHeaders();
			if (headers != null) {
				headers.keySet().removeIf(name -> SENSITIVE_HEADERS.contains(name.toLowerCase(Locale.ROOT)));
			}
			Map<String, String> envs = request.getEnvs();
			if (envs != null) {
				envs.remove("REMOTE_ADDR");
			}
		}
		return event;
	}

}
