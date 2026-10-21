package com.smartportal.repository;

import com.smartportal.model.User;
import com.smartportal.model.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByMobile(String mobile);
    List<User> findByRole(UserRole role);
}