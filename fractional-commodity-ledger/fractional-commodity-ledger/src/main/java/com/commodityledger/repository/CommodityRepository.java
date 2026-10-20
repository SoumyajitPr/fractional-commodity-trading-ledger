package com.commodityledger.repository;

import com.commodityledger.entity.Commodity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommodityRepository extends JpaRepository<Commodity, Long> {

    Optional<Commodity> findBySymbol(String symbol);

    List<Commodity> findAllByOrderBySymbolAsc();
}
