package com.bookingsquadra.repository;

import com.bookingsquadra.entity.City;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CityRepository extends JpaRepository<City, Integer> {

    @Query(value = """
            SELECT *
            FROM public.cities
            WHERE name ILIKE CONCAT('%', :q, '%')
            ORDER BY name ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<City> searchByName(@Param("q") String q, @Param("limit") int limit);
}
