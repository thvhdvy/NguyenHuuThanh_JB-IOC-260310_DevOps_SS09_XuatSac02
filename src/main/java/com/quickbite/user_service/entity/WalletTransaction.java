package com.quickbite.user_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/** Sổ cái ví: mỗi lần số dư đổi là một dòng. */
@Entity
@Table(name = "wallet_transactions", indexes = @Index(columnList = "userId"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WalletTransaction {

	public enum Type {
		DEPOSIT, PAYMENT, REFUND, EARNING
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Type type;

	/** Có dấu: âm khi trừ ví, dương khi cộng. */
	@Column(nullable = false, precision = 15, scale = 2)
	private BigDecimal amount;

	@Column(nullable = false, precision = 15, scale = 2)
	private BigDecimal balanceAfter;

	/** Đơn hàng liên quan (null với nạp tiền). Dùng để chống trừ/hoàn tiền trùng. */
	private Long orderId;

	@Column(nullable = false)
	private Instant createdAt;
}
