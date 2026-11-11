package com.quickbite.user_service.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false, unique = true)
	private String email;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Role role;

	/** Không có getter chuẩn nên không bao giờ bị serialize ra JSON. Null với user tạo trước khi có đăng nhập. */
	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private String passwordHash;

	public User(Long id, String name, String email, Role role) {
		this.id = id;
		this.name = name;
		this.email = email;
		this.role = role;
	}

	public String hash() {
		return passwordHash;
	}

	public void changePasswordHash(String hash) {
		this.passwordHash = hash;
	}
}
