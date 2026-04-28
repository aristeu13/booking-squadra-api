package com.bookingsquadra.repository;

import java.util.UUID;

public interface VenueDistanceProjection {
    UUID getId();
    String getName();
    String getDescription();
    String getImageUrl();
    String getAddress();
    String getCity();
    String getStateCode();
    String[] getSports();
    String getAmenities();
    Integer getPriceCents();
    Double getDistanceKm();
}
