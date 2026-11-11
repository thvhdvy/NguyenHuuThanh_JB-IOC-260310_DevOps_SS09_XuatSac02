package com.quickbite.user_service.repository;

import com.quickbite.user_service.entity.WalletTransaction;
import com.quickbite.user_service.entity.WalletTransaction.Type;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

	List<WalletTransaction> findByUserIdOrderByIdDesc(Long userId);

	boolean existsByUserIdAndTypeAndOrderId(Long userId, Type type, Long orderId);
}
