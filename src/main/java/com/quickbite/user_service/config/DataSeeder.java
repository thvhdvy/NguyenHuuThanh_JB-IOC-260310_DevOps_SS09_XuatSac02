package com.quickbite.user_service.config;

import com.quickbite.user_service.entity.Role;
import com.quickbite.user_service.entity.User;
import com.quickbite.user_service.entity.UserWallet;
import com.quickbite.user_service.repository.UserRepository;
import com.quickbite.user_service.repository.UserWalletRepository;
import com.quickbite.user_service.security.PasswordHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Dữ liệu mẫu cho môi trường dev. Chạy lặp an toàn: user nào thiếu thì tạo, user mẫu chưa có mật khẩu
 * (tạo trước khi có đăng nhập) thì được gán mật khẩu mặc định. Tắt hẳn bằng SEED_ENABLED=false.
 */
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

	private final UserRepository users;
	private final UserWalletRepository wallets;

	@Value("${seed.enabled:true}")
	private boolean enabled;

	@Value("${seed.password:Passw0rd!}")
	private String seedPassword;

	@Override
	public void run(String... args) {
		if (!enabled) {
			return;
		}
		seed("Alice", "alice@quickbite.dev", Role.CUSTOMER, "500000");
		seed("Bob", "bob@quickbite.dev", Role.CUSTOMER, "10000");
		seed("Dave", "dave@quickbite.dev", Role.DRIVER, "0");
		seed("Eve", "eve@quickbite.dev", Role.DRIVER, "0");
		seed("Admin", "admin@quickbite.dev", Role.ADMIN, "0");
	}

	private void seed(String name, String email, Role role, String balance) {
		User user = users.findByEmail(email).orElse(null);
		if (user == null) {
			user = new User(null, name, email, role);
			user.changePasswordHash(PasswordHasher.hash(seedPassword));
			user = users.save(user);
			wallets.save(new UserWallet(null, user.getId(), new BigDecimal(balance)));
		} else if (user.hash() == null) {
			user.changePasswordHash(PasswordHasher.hash(seedPassword));
			users.save(user);
		}
	}
}
