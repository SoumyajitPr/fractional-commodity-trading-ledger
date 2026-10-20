package com.commodityledger.repository;

import com.commodityledger.entity.LedgerTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, Long> {

    @Query("select t from LedgerTransaction t left join fetch t.entries where t.userId = :userId order by t.id desc")
    List<LedgerTransaction> findByUserIdWithEntries(@Param("userId") Long userId);

    @Query("select t from LedgerTransaction t left join fetch t.entries where t.tradeId = :tradeId")
    Optional<LedgerTransaction> findByTradeIdWithEntries(@Param("tradeId") Long tradeId);

    @Query("select t from LedgerTransaction t left join fetch t.entries where t.tradeId in :tradeIds")
    List<LedgerTransaction> findByTradeIdsWithEntries(@Param("tradeIds") Collection<Long> tradeIds);
}
