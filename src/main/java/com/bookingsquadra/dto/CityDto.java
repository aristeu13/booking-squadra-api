package com.bookingsquadra.dto;

public record CityDto(
        Integer id,
        String name,
        String stateCode,
        Double latitude,
        Double longitude
) {}
