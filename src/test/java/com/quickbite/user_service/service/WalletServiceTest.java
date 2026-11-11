package com.quickbite.user_service.service;

import com.quickbite.user_service.entity.UserWallet;
import com.quickbite.user_service.entity.WalletTransaction.Type;
import com.quickbite.user_service.repository.UserWalletRepository;
import com.quickbite.user_service.repository.WalletTransactionRepository;
import com.quickbite.user_service.service.WalletService.PaymentResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WalletServiceTest {

	private UserWalletRepository wallets;
	private WalletTransactionRepository ledger;
	private WalletService service;
	private UserWallet wallet;

	@BeforeEach
	void setUp() {
		wallets = mock(UserWalletRepository.class);
		ledger = mock(WalletTransactionRepository.class);
		service = new WalletService(wallets, ledger);
		wallet = new UserWallet(1L, 1L, new BigDecimal("100"));
		when(wallets.findWithLockByUserId(1L)).thenReturn(Optional.of(wallet));
	}

	@Test
	void payDeductsAndRecordsLedger() {
		PaymentResult r = service.pay(1L, new BigDecimal("40"), 9L);

		assertTrue(r.success());
		assertEquals(new BigDecimal("60"), wallet.getBalance());
		verify(ledger).save(any());
	}

	@Test
	void payWithInsufficientBalanceDoesNotChangeBalance() {
		PaymentResult r = service.pay(1L, new BigDecimal("500"), 9L);

		assertFalse(r.success());
		assertEquals(new BigDecimal("100"), wallet.getBalance());
		verify(ledger, never()).save(any());
	}

	@Test
	void payingSameOrderTwiceChargesOnce() {
		when(ledger.existsByUserIdAndTypeAndOrderId(1L, Type.PAYMENT, 9L)).thenReturn(true);

		PaymentResult r = service.pay(1L, new BigDecimal("40"), 9L);

		assertTrue(r.success());
		assertEquals(new BigDecimal("100"), wallet.getBalance());
	}

	@Test
	void refundOnlyAfterPaymentAndOnlyOnce() {
		assertThrows(ResponseStatusException.class, () -> service.refund(1L, new BigDecimal("40"), 9L));

		when(ledger.existsByUserIdAndTypeAndOrderId(1L, Type.PAYMENT, 9L)).thenReturn(true);
		service.refund(1L, new BigDecimal("40"), 9L);
		assertEquals(new BigDecimal("140"), wallet.getBalance());

		when(ledger.existsByUserIdAndTypeAndOrderId(1L, Type.REFUND, 9L)).thenReturn(true);
		service.refund(1L, new BigDecimal("40"), 9L);
		assertEquals(new BigDecimal("140"), wallet.getBalance());
	}

	@Test
	void payRefundAndEarnRequireAnOrderId() {
		assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.pay(1L, BigDecimal.TEN, null))
				.getStatusCode().value());
		assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.refund(1L, BigDecimal.TEN, null))
				.getStatusCode().value());
		assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.earn(1L, BigDecimal.TEN, null))
				.getStatusCode().value());
		assertEquals(new BigDecimal("100"), wallet.getBalance());
	}

	@Test
	void nonPositiveAmountIsRejected() {
		assertThrows(ResponseStatusException.class, () -> service.deposit(1L, BigDecimal.ZERO));
	}
}
