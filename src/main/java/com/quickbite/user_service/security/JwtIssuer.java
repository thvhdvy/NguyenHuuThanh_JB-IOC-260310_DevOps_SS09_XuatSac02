package com.quickbite.user_service.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/** Cấp JWT HS256. Secret dùng chung với gateway (biến môi trường JWT_SECRET). */
@Component
public class JwtIssuer {

	private final byte[] secret;
	private final Duration ttl;

	public JwtIssuer(@Value("${jwt.secret}") String secret, @Value("${jwt.ttl:8h}") Duration ttl) {
		if (secret.length() < 32) {
			throw new IllegalStateException("jwt.secret must be at least 32 characters");
		}
		this.secret = secret.getBytes(StandardCharsets.UTF_8);
		this.ttl = ttl;
	}

	public Duration ttl() {
		return ttl;
	}

	public String issue(Long userId, String role) {
		Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
		String header = b64.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
		String payload = b64.encodeToString(("{\"sub\":\"" + userId + "\",\"role\":\"" + role + "\",\"exp\":"
				+ Instant.now().plus(ttl).getEpochSecond() + "}").getBytes(StandardCharsets.UTF_8));
		String signingInput = header + "." + payload;
		return signingInput + "." + b64.encodeToString(hmac(signingInput));
	}

	private byte[] hmac(String data) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret, "HmacSHA256"));
			return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}
}
