package com.fluveny.fluveny_backend.learning.receipt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Confere um recibo do {@link ReceiptSigner} (história 1.12), para a migração da história 2.6:
 * prefixo {@code v1}, as duas partes em base64url, o HMAC comparado em tempo constante e só depois
 * o JSON. Não consome o {@code nonce}; isso é da migração.
 *
 * <p>Na rotação da chave (história 2.6), o recibo assinado com a chave anterior também vale; a
 * anterior nunca assina. O bean sai do {@link ReceiptConfig}.
 */
public class ReceiptVerifier {

    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final ReceiptKey key;

    private final ReceiptKey previousKey;

    public ReceiptVerifier(ReceiptKey key) {
        this(key, null);
    }

    /**
     * @param previousKey a chave anterior, só para conferir; nula sem rotação
     */
    public ReceiptVerifier(ReceiptKey key, ReceiptKey previousKey) {
        this.key = key;
        this.previousKey = previousKey;
    }

    /**
     * @return o payload do recibo
     * @throws InvalidReceiptException se o recibo está malformado, foi adulterado ou foi assinado
     *     com outra chave
     */
    public ReceiptPayload verify(String receipt) {
        if (receipt == null) {
            throw new InvalidReceiptException("recibo ausente");
        }
        String[] parts = receipt.split("\\.", -1);
        if (parts.length != 3 || !ReceiptSigner.VERSION.equals(parts[0])) {
            throw new InvalidReceiptException("formato ou versão desconhecidos");
        }
        byte[] json;
        byte[] presented;
        try {
            json = DECODER.decode(parts[1]);
            presented = DECODER.decode(parts[2]);
        } catch (IllegalArgumentException ex) {
            throw new InvalidReceiptException("base64url inválido");
        }
        byte[] signed = (parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII);
        boolean valid = MessageDigest.isEqual(this.key.hmac(signed), presented);
        if (!valid && this.previousKey != null) {
            valid = MessageDigest.isEqual(this.previousKey.hmac(signed), presented);
        }
        if (!valid) {
            throw new InvalidReceiptException("HMAC não confere");
        }
        ReceiptPayload payload;
        try {
            payload = ReceiptSigner.MAPPER.readValue(json, ReceiptPayload.class);
        } catch (RuntimeException ex) {
            throw new InvalidReceiptException("JSON inválido");
        }
        if (payload.deviceId() == null
                || payload.unitKey() == null
                || payload.versionId() == null
                || payload.activityKey() == null
                || payload.outcome() == null
                || payload.nonce() == null
                || payload.issuedAt() == null) {
            throw new InvalidReceiptException("campo obrigatório ausente");
        }
        return payload;
    }

    /** Recibo rejeitado. A mensagem é para o log, nunca para o cliente. */
    public static class InvalidReceiptException extends RuntimeException {

        InvalidReceiptException(String reason) {
            super("Recibo inválido: " + reason);
        }
    }
}
