package com.commodityledger.repository;

import com.commodityledger.entity.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface TradeRepository extends JpaRepository<Trade, Long> {

    @Query("select t from Trade t join fetch t.commodity where t.id = :id")
    Optional<Trade> findByIdWithCommodity(@Param("id") Long id);

    @Query("select t from Trade t join fetch t.commodity where t.user.id = :userId order by t.id desc")
    List<Trade> findByUserIdWithCommodity(@Param("userId") Long userId);

    @Query("select t from Trade t join fetch t.commodity where t.user.id = :userId and t.clientOrderId = :clientOrderId")
    Optional<Trade> findByUserIdAndClientOrderId(@Param("userId") Long userId,
                                                 @Param("clientOrderId") String clientOrderId);

    /** Total realised profit/loss of a user; null when the user has never sold. */
    @Query("select sum(t.realizedPnl) from Trade t where t.user.id = :userId")
    BigDecimal sumRealizedPnl(@Param("userId") Long userId);
}
