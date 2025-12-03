package com.miguel.spyzer.repository;

import com.miguel.spyzer.entities.HistoricalDataPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface HistoricalDataRepository extends JpaRepository<HistoricalDataPoint, Long> {
    
    @Query(value = "SELECT * FROM historical_data_point WHERE symbol = ?1 ORDER BY date DESC LIMIT ?2", nativeQuery = true)
    List<HistoricalDataPoint> findBySymbolOrderByDateDesc(String symbol, int limit);
    
    void deleteBySymbol(String symbol);
}