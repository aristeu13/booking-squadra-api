package com.bookingsquadra.service;

import com.bookingsquadra.dto.CityDto;
import com.bookingsquadra.entity.City;
import com.bookingsquadra.repository.CityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CityService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;
    private static final int MIN_QUERY_LENGTH = 2;

    private final CityRepository cityRepository;

    public CityService(CityRepository cityRepository) {
        this.cityRepository = cityRepository;
    }

    @Transactional(readOnly = true)
    public List<CityDto> search(String query, Integer limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        String q = query.trim();
        if (q.length() < MIN_QUERY_LENGTH) {
            return List.of();
        }

        int effectiveLimit = DEFAULT_LIMIT;
        if (limit != null) {
            effectiveLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
        }

        return cityRepository.searchByName(q, effectiveLimit)
                .stream()
                .map(CityService::toDto)
                .toList();
    }

    private static CityDto toDto(City c) {
        return new CityDto(c.getId(), c.getName(), c.getStateCode(), c.getLatitude(), c.getLongitude());
    }
}
