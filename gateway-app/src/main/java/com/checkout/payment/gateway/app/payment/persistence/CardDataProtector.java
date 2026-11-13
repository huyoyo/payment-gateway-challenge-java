package com.checkout.payment.gateway.app.payment.persistence;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * AES-GCM encryption for sensitive card data at rest. The per-record AES key is derived from the
 * randomly generated in-memory master key and payment id with HMAC-SHA256, so a ciphertext cannot
 * be moved between records.
 */
@Service("gateway.app.payment.persistence.card-data-protector")
public class CardDataProtector {

  private static final String AES_GCM = "AES/GCM/NoPadding";
  private static final String HMAC_SHA256 = "HmacSHA256";
  private static final int IV_BYTES = 12;
  private static final int TAG_BITS = 128;
  private static final int MASTER_KEY_BYTES = 32;

  private final byte[] masterKey;
  private final SecureRandom secureRandom = new SecureRandom();

  public CardDataProtector() {
    this.masterKey = new byte[MASTER_KEY_BYTES];
    secureRandom.nextBytes(masterKey);
  }

  /**
   * Encrypts a card value with a key derived from the payment id.
   *
   * @param paymentId payment id
   * @param plaintext value to encrypt
   * @return base64 IV + ciphertext
   */
  public String encrypt(UUID paymentId, String plaintext) {
    Objects.requireNonNull(paymentId, "paymentId must not be null");
    Objects.requireNonNull(plaintext, "plaintext must not be null");
    try {
      byte[] iv = new byte[IV_BYTES];
      secureRandom.nextBytes(iv);
      Cipher cipher = Cipher.getInstance(AES_GCM);
      cipher.init(Cipher.ENCRYPT_MODE, keyFor(paymentId), new GCMParameterSpec(TAG_BITS, iv));
      byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      byte[] payload = new byte[iv.length + ciphertext.length];
      System.arraycopy(iv, 0, payload, 0, iv.length);
      System.arraycopy(ciphertext, 0, payload, iv.length, ciphertext.length);
      return Base64.getEncoder().encodeToString(payload);
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException("Could not encrypt card data", ex);
    }
  }

  /**
   * Decrypts a value previously encrypted for the same payment id.
   *
   * @param paymentId payment id
   * @param encoded base64 IV + ciphertext
   * @return decrypted value
   */
  public String decrypt(UUID paymentId, String encoded) {
    Objects.requireNonNull(paymentId, "paymentId must not be null");
    Objects.requireNonNull(encoded, "encoded must not be null");
    byte[] payload;
    try {
      payload = Base64.getDecoder().decode(encoded);
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Card data is not valid base64", ex);
    }
    if (payload.length <= IV_BYTES) {
      throw new IllegalArgumentException("Card data payload is too short");
    }
    try {
      byte[] iv = new byte[IV_BYTES];
      System.arraycopy(payload, 0, iv, 0, IV_BYTES);
      Cipher cipher = Cipher.getInstance(AES_GCM);
      cipher.init(Cipher.DECRYPT_MODE, keyFor(paymentId), new GCMParameterSpec(TAG_BITS, iv));
      byte[] plaintext = cipher.doFinal(payload, IV_BYTES, payload.length - IV_BYTES);
      return new String(plaintext, StandardCharsets.UTF_8);
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException("Could not decrypt card data", ex);
    }
  }

  private SecretKeySpec keyFor(UUID paymentId) throws GeneralSecurityException {
    Mac mac = Mac.getInstance(HMAC_SHA256);
    mac.init(new SecretKeySpec(masterKey, HMAC_SHA256));
    byte[] derived = mac.doFinal(paymentId.toString().getBytes(StandardCharsets.UTF_8));
    return new SecretKeySpec(derived, "AES");
  }
}
