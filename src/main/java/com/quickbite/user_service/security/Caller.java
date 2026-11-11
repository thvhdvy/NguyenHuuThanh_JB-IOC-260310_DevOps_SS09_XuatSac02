package com.quickbite.user_service.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

/**
 * Danh tính người gọi do gateway gắn (gateway luôn xoá header giả từ client):
 * <ul>
 * <li>X-User-Role vắng mặt: gọi nội bộ giữa các service trong mạng docker, không đi qua gateway;</li>
 * <li>X-User-Role=ANONYMOUS: request công khai chưa đăng nhập (chưa có id);</li>
 * <li>còn lại: người dùng đã xác thực với X-User-Id và X-User-Role.</li>
 * </ul>
 */
public record Caller(Long id, String role) {

	public static Caller of(HttpServletRequest req) {
		String role = req.getHeader("X-User-Role");
		if (role == null) {
			return new Caller(null, null);
		}
		if ("ANONYMOUS".equals(role)) {
			return new Caller(null, role);
		}
		try {
			return new Caller(Long.valueOf(req.getHeader("X-User-Id")), role);
		} catch (NumberFormatException e) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid identity header");
		}
	}

	public boolean internal() {
		return role == null;
	}

	public boolean admin() {
		return "ADMIN".equals(role);
	}

	public boolean hasRole(String expected) {
		return expected.equals(role);
	}

	/** Endpoint chỉ dành cho service-to-service. */
	public void requireInternal() {
		if (!internal()) {
			throw forbidden("Internal endpoint");
		}
	}

	public void requireAdmin() {
		if (!internal() && !admin()) {
			throw forbidden("Admin only");
		}
	}

	public void requireSelfOrAdmin(Long userId) {
		if (!internal() && !admin() && !Objects.equals(id, userId)) {
			throw forbidden("You can only access your own data");
		}
	}

	private static ResponseStatusException forbidden(String message) {
		return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
	}
}
