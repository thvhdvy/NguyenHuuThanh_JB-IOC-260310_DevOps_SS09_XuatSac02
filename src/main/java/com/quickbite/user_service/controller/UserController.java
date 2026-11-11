package com.quickbite.user_service.controller;

import com.quickbite.user_service.entity.Role;
import com.quickbite.user_service.entity.User;
import com.quickbite.user_service.entity.UserWallet;
import com.quickbite.user_service.entity.WalletTransaction;
import com.quickbite.user_service.repository.UserRepository;
import com.quickbite.user_service.repository.UserWalletRepository;
import com.quickbite.user_service.security.Caller;
import com.quickbite.user_service.security.PasswordHasher;
import com.quickbite.user_service.service.WalletService;
import com.quickbite.user_service.service.WalletService.PaymentResult;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

	public record CreateUserRequest(String name, String email, Role role, BigDecimal initialBalance, String password) {
	}

	public record UpdateUserRequest(String name, String email) {
	}

	/** orderId bắt buộc với pay/refund/earn; dùng để kiểm tra và chống trừ/hoàn tiền trùng. */
	public record AmountRequest(BigDecimal amount, Long orderId) {
	}

	private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
	private static final int MIN_PASSWORD_LENGTH = 8;

	private final UserRepository users;
	private final UserWalletRepository wallets;
	private final WalletService walletService;

	/** Danh sách người dùng: chỉ ADMIN (hoặc service nội bộ, ví dụ order-service lấy danh sách tài xế). */
	@GetMapping
	public List<User> list(@RequestParam(required = false) Role role, HttpServletRequest http) {
		Caller.of(http).requireAdmin();
		return role == null ? users.findAll() : users.findByRole(role);
	}

	@GetMapping("/{id}")
	public User get(@PathVariable Long id, HttpServletRequest http) {
		Caller caller = Caller.of(http);
		// Tài xế/khách chỉ xem hồ sơ của chính mình; ADMIN và nội bộ xem được tất cả.
		caller.requireSelfOrAdmin(id);
		return find(id);
	}

	/** Đăng ký công khai (CUSTOMER / DRIVER). Chỉ ADMIN mới tạo được ADMIN hoặc cấp số dư ban đầu. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Transactional
	public User create(@RequestBody CreateUserRequest req, HttpServletRequest http) {
		Caller caller = Caller.of(http);
		if (isBlank(req.name()) || isBlank(req.email()) || req.role() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name, email, role are required");
		}
		if (!EMAIL.matcher(req.email()).matches()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email is invalid");
		}
		if (req.password() == null || req.password().length() < MIN_PASSWORD_LENGTH) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"password must be at least " + MIN_PASSWORD_LENGTH + " characters");
		}
		if (req.initialBalance() != null && req.initialBalance().signum() < 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "initialBalance must be >= 0");
		}
		boolean privileged = caller.internal() || caller.admin();
		if (req.role() == Role.ADMIN && !privileged) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot register as ADMIN");
		}
		if (req.initialBalance() != null && req.initialBalance().signum() > 0 && !privileged) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "initialBalance can only be set by an admin");
		}
		if (users.existsByEmail(req.email())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
		}
		User user = new User(null, req.name(), req.email(), req.role());
		user.changePasswordHash(PasswordHasher.hash(req.password()));
		user = users.save(user);
		BigDecimal balance = req.initialBalance() == null ? BigDecimal.ZERO : req.initialBalance();
		wallets.save(new UserWallet(null, user.getId(), balance));
		return user;
	}

	@PutMapping("/{id}")
	public User update(@PathVariable Long id, @RequestBody UpdateUserRequest req, HttpServletRequest http) {
		Caller.of(http).requireSelfOrAdmin(id);
		User user = find(id);
		if (!isBlank(req.name())) {
			user.setName(req.name());
		}
		if (!isBlank(req.email()) && !req.email().equals(user.getEmail())) {
			if (!EMAIL.matcher(req.email()).matches()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email is invalid");
			}
			if (users.existsByEmail(req.email())) {
				throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
			}
			user.setEmail(req.email());
		}
		return users.save(user);
	}

	@GetMapping("/{id}/wallet")
	public UserWallet wallet(@PathVariable Long id, HttpServletRequest http) {
		Caller.of(http).requireSelfOrAdmin(id);
		return walletService.get(id);
	}

	@GetMapping("/{id}/wallet/transactions")
	public List<WalletTransaction> transactions(@PathVariable Long id, HttpServletRequest http) {
		Caller.of(http).requireSelfOrAdmin(id);
		return walletService.transactions(id);
	}

	@PostMapping("/{id}/wallet/deposit")
	public UserWallet deposit(@PathVariable Long id, @RequestBody AmountRequest req, HttpServletRequest http) {
		Caller.of(http).requireSelfOrAdmin(id);
		return walletService.deposit(id, req.amount());
	}

	/** pay / refund / earn chỉ order-service được gọi (không qua gateway). */
	@PostMapping("/{id}/wallet/pay")
	public PaymentResult pay(@PathVariable Long id, @RequestBody AmountRequest req, HttpServletRequest http) {
		Caller.of(http).requireInternal();
		return walletService.pay(id, req.amount(), req.orderId());
	}

	@PostMapping("/{id}/wallet/refund")
	public PaymentResult refund(@PathVariable Long id, @RequestBody AmountRequest req, HttpServletRequest http) {
		Caller.of(http).requireInternal();
		return walletService.refund(id, req.amount(), req.orderId());
	}

	@PostMapping("/{id}/wallet/earn")
	public PaymentResult earn(@PathVariable Long id, @RequestBody AmountRequest req, HttpServletRequest http) {
		Caller.of(http).requireInternal();
		return walletService.earn(id, req.amount(), req.orderId());
	}

	private User find(Long id) {
		return users.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + id));
	}

	private static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}
}
