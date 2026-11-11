package com.quickbite.user_service.service;

import com.quickbite.user_service.entity.UserWallet;
import com.quickbite.user_service.entity.WalletTransaction;
import com.quickbite.user_service.entity.WalletTransaction.Type;
import com.quickbite.user_service.repository.UserWalletRepository;
import com.quickbite.user_service.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WalletService {

	public record PaymentResult(boolean success, BigDecimal balance, String message) {
	}

	private final UserWalletRepository wallets;
	private final WalletTransactionRepository ledger;

	@Transactional(readOnly = true)
	public UserWallet get(Long userId) {
		return wallets.findByUserId(userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet not found for user " + userId));
	}

	@Transactional(readOnly = true)
	public List<WalletTransaction> transactions(Long userId) {
		get(userId);
		return ledger.findByUserIdOrderByIdDesc(userId);
	}

	@Transactional
	public UserWallet deposit(Long userId, BigDecimal amount) {
		requirePositive(amount);
		UserWallet wallet = lock(userId);
		credit(wallet, amount, Type.DEPOSIT, null);
		return wallet;
	}

	/**
	 * Trừ tiền nếu đủ số dư; không đủ thì trả success=false (không ném lỗi).
	 * Gọi lại với cùng orderId không trừ lần hai.
	 */
	@Transactional
	public PaymentResult pay(Long userId, BigDecimal amount, Long orderId) {
		requirePositive(amount);
		requireOrder(orderId);
		UserWallet wallet = lock(userId);
		if (ledger.existsByUserIdAndTypeAndOrderId(userId, Type.PAYMENT, orderId)) {
			return new PaymentResult(true, wallet.getBalance(), "Already paid");
		}
		if (wallet.getBalance().compareTo(amount) < 0) {
			return new PaymentResult(false, wallet.getBalance(), "Insufficient balance");
		}
		wallet.setBalance(wallet.getBalance().subtract(amount));
		record(wallet, amount.negate(), Type.PAYMENT, orderId);
		return new PaymentResult(true, wallet.getBalance(), "Paid");
	}

	/** Giao dịch bù: cộng lại tiền đã trừ. Mỗi đơn chỉ được hoàn một lần. */
	@Transactional
	public PaymentResult refund(Long userId, BigDecimal amount, Long orderId) {
		requirePositive(amount);
		requireOrder(orderId);
		UserWallet wallet = lock(userId);
		if (!ledger.existsByUserIdAndTypeAndOrderId(userId, Type.PAYMENT, orderId)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Order " + orderId + " was never paid");
		}
		if (ledger.existsByUserIdAndTypeAndOrderId(userId, Type.REFUND, orderId)) {
			return new PaymentResult(true, wallet.getBalance(), "Already refunded");
		}
		credit(wallet, amount, Type.REFUND, orderId);
		return new PaymentResult(true, wallet.getBalance(), "Refunded");
	}

	/** Tiền công tài xế khi giao xong đơn; mỗi đơn chỉ nhận một lần. */
	@Transactional
	public PaymentResult earn(Long userId, BigDecimal amount, Long orderId) {
		requirePositive(amount);
		requireOrder(orderId);
		UserWallet wallet = lock(userId);
		if (ledger.existsByUserIdAndTypeAndOrderId(userId, Type.EARNING, orderId)) {
			return new PaymentResult(true, wallet.getBalance(), "Already credited");
		}
		credit(wallet, amount, Type.EARNING, orderId);
		return new PaymentResult(true, wallet.getBalance(), "Credited");
	}

	private void credit(UserWallet wallet, BigDecimal amount, Type type, Long orderId) {
		wallet.setBalance(wallet.getBalance().add(amount));
		record(wallet, amount, type, orderId);
	}

	private void record(UserWallet wallet, BigDecimal signedAmount, Type type, Long orderId) {
		ledger.save(new WalletTransaction(null, wallet.getUserId(), type, signedAmount, wallet.getBalance(), orderId,
				Instant.now()));
	}

	private UserWallet lock(Long userId) {
		return wallets.findWithLockByUserId(userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet not found for user " + userId));
	}

	/** pay/refund/earn luôn gắn với một đơn để có thể kiểm tra và chống trùng. */
	private void requireOrder(Long orderId) {
		if (orderId == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "orderId is required");
		}
	}

	private void requirePositive(BigDecimal amount) {
		if (amount == null || amount.signum() <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be > 0");
		}
	}
}
