package com.commodityledger.repository;

import com.commodityledger.entity.Holding;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HoldingRepository extends JpaRepository<Holding, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from Holding h where h.user.id = :userId and h.commodity.id = :commodityId")
    Optional<Holding> findForUpdate(@Param("userId") Long userId, @Param("commodityId") Long commodityId);

    @Query("select h from Holding h where h.user.id = :userId and h.commodity.id = :commodityId")
    Optional<Holding> findByUserAndCommodity(@Param("userId") Long userId, @Param("commodityId") Long commodityId);

    @Query("select h from Holding h join fetch h.commodity c where h.user.id = :userId and h.quantity > 0 order by c.symbol")
    List<Holding> findOpenPositions(@Param("userId") Long userId);

    @Query("select h from Holding h join fetch h.commodity")
    List<Holding> findAllWithCommodity();
}
