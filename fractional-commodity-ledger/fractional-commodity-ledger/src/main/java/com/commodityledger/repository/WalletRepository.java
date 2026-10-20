package com.commodityledger.repository;

import com.commodityledger.entity.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    /**
     * SELECT ... FOR UPDATE on the user's wallet row. Every money-moving operation for a
     * user takes this lock first, so concurrent requests for the same user run one at a
     * time and can never spend the same rupee twice.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.user.id = :userId")
    Optional<Wallet> findByUserIdForUpdate(@Param("userId") Long userId);

    @Query("select w from Wallet w join fetch w.user where w.user.id = :userId")
    Optional<Wallet> findByUserIdWithUser(@Param("userId") Long userId);

    @Query("select w from Wallet w join fetch w.user u order by u.id")
    List<Wallet> findAllWithUser();
}
