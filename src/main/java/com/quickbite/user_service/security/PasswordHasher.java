package com.quickbite.user_service.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** PBKDF2-HMAC-SHA256 có salt; định dạng lưu: iterations:salt:hash (base64). */
public final class PasswordHasher {

	private static final int ITERATIONS = 120_000;
	private static final int KEY_BITS = 256;
	private static final SecureRandom RANDOM = new SecureRandom();

	private PasswordHasher() {
	}

	public static String hash(String password) {
		byte[] salt = new byte[16];
		RANDOM.nextBytes(salt);
		return ITERATIONS + ":" + Base64.getEncoder().encodeToString(salt) + ":"
				+ Base64.getEncoder().encodeToString(derive(password, salt, ITERATIONS));
	}

	public static boolean matches(String password, String stored) {
		if (password == null || stored == null) {
			return false;
		}
		String[] parts = stored.split(":");
		if (parts.length != 3) {
			return false;
		}
		try {
			byte[] expected = Base64.getDecoder().decode(parts[2]);
			byte[] actual = derive(password, Base64.getDecoder().decode(parts[1]), Integer.parseInt(parts[0]));
			return MessageDigest.isEqual(expected, actual);
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	private static byte[] derive(String password, byte[] salt, int iterations) {
		try {
			return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
					.generateSecret(new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)).getEncoded();
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}
}
