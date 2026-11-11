package com.quickbite.user_service.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class SecurityTest {

	@Test
	void passwordHashVerifiesOnlyTheRightPassword() {
		String hash = PasswordHasher.hash("Passw0rd!");

		assertTrue(PasswordHasher.matches("Passw0rd!", hash));
		assertFalse(PasswordHasher.matches("passw0rd!", hash));
		assertNotEquals(hash, PasswordHasher.hash("Passw0rd!"), "salt must differ");
		assertFalse(PasswordHasher.matches("x", null));
		assertFalse(PasswordHasher.matches("x", "not-a-hash"));
	}

	@Test
	void issuedTokenHasThreePartsAndExpectedClaims() {
		JwtIssuer issuer = new JwtIssuer("test-secret-with-at-least-32-characters!!", Duration.ofHours(1));

		String[] parts = issuer.issue(7L, "DRIVER").split("\\.");

		assertEquals(3, parts.length);
		String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
		assertTrue(payload.contains("\"sub\":\"7\"") && payload.contains("\"role\":\"DRIVER\"") && payload.contains("\"exp\":"));
	}

	@Test
	void shortSecretIsRejected() {
		assertThrows(IllegalStateException.class, () -> new JwtIssuer("short", Duration.ofHours(1)));
	}

	private static MockHttpServletRequest request(String id, String role) {
		MockHttpServletRequest req = new MockHttpServletRequest();
		if (id != null) {
			req.addHeader("X-User-Id", id);
		}
		if (role != null) {
			req.addHeader("X-User-Role", role);
		}
		return req;
	}

	@Test
	void callerRulesFollowIdentity() {
		Caller internal = Caller.of(request(null, null));
		Caller customer = Caller.of(request("5", "CUSTOMER"));
		Caller admin = Caller.of(request("1", "ADMIN"));
		Caller anonymous = Caller.of(request(null, "ANONYMOUS"));

		assertTrue(internal.internal());
		assertDoesNotThrow(() -> internal.requireSelfOrAdmin(99L));
		assertDoesNotThrow(() -> customer.requireSelfOrAdmin(5L));
		assertThrows(ResponseStatusException.class, () -> customer.requireSelfOrAdmin(6L));
		assertDoesNotThrow(() -> admin.requireSelfOrAdmin(6L));
		assertThrows(ResponseStatusException.class, customer::requireAdmin);
		assertThrows(ResponseStatusException.class, customer::requireInternal);
		assertThrows(ResponseStatusException.class, anonymous::requireInternal);
		assertThrows(ResponseStatusException.class, () -> anonymous.requireSelfOrAdmin(1L));
	}
}
