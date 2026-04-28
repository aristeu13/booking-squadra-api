package com.bookingsquadra.service;

import com.bookingsquadra.dto.CityDto;
import com.bookingsquadra.entity.City;
import com.bookingsquadra.repository.CityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CityService {

    private static final int MAX_RESULTS = 50;

    private final CityRepository cityRepository;

    public CityService(CityRepository cityRepository) {
        this.cityRepository = cityRepository;
    }

    @Transactional(readOnly = true)
    public List<CityDto> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return cityRepository.searchByName(query.trim(), MAX_RESULTS)
                .stream()
                .map(CityService::toDto)
                .toList();
    }

    private static CityDto toDto(City c) {
        return new CityDto(c.getId(), c.getName(), c.getStateCode(), c.getLatitude(), c.getLongitude());
    }
}
