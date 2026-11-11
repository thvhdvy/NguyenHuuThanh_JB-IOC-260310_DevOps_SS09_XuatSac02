package com.quickbite.user_service.repository;

import com.quickbite.user_service.entity.Role;
import com.quickbite.user_service.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {

	List<User> findByRole(Role role);

	boolean existsByEmail(String email);

	java.util.Optional<User> findByEmail(String email);
}
