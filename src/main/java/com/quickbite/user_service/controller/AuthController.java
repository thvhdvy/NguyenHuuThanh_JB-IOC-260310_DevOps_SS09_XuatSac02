package com.quickbite.user_service.controller;

import com.quickbite.user_service.entity.User;
import com.quickbite.user_service.repository.UserRepository;
import com.quickbite.user_service.security.JwtIssuer;
import com.quickbite.user_service.security.PasswordHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/users/auth")
@RequiredArgsConstructor
public class AuthController {

	public record LoginRequest(String email, String password) {
	}

	public record LoginResponse(String token, String tokenType, long expiresIn, User user) {
	}

	private final UserRepository users;
	private final JwtIssuer jwt;

	@PostMapping("/login")
	public LoginResponse login(@RequestBody LoginRequest req) {
		if (req.email() == null || req.password() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email and password are required");
		}
		// Cùng một thông báo cho "không có user" và "sai mật khẩu" để không lộ email nào tồn tại.
		User user = users.findByEmail(req.email())
				.filter(u -> PasswordHasher.matches(req.password(), u.hash()))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
		return new LoginResponse(jwt.issue(user.getId(), user.getRole().name()), "Bearer", jwt.ttl().toSeconds(),
				user);
	}
}
