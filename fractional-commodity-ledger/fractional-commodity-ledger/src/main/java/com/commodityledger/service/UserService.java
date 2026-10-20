package com.commodityledger.service;

import com.commodityledger.dto.CreateUserRequest;
import com.commodityledger.dto.UserResponse;
import com.commodityledger.entity.User;
import com.commodityledger.entity.Wallet;
import com.commodityledger.exception.DuplicateResourceException;
import com.commodityledger.exception.ResourceNotFoundException;
import com.commodityledger.repository.UserRepository;
import com.commodityledger.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;

    public UserService(UserRepository userRepository, WalletRepository walletRepository) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
    }

    /** Creates the user and an empty INR wallet in one transaction. */
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("A user with email '" + email + "' already exists");
        }
        User user = userRepository.save(new User(request.name().trim(), email));
        Wallet wallet = walletRepository.save(new Wallet(user));
        return UserResponse.from(wallet);
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(Long userId) {
        return walletRepository.findByUserIdWithUser(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers() {
        return walletRepository.findAllWithUser().stream().map(UserResponse::from).toList();
    }
}
