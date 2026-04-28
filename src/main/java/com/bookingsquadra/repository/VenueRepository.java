package com.bookingsquadra.repository;

import com.bookingsquadra.entity.Venue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface VenueRepository extends JpaRepository<Venue, UUID> {

    // Mirrors the public.get_squadra_venues RPC: Haversine distance against
    // user lat/lon, filter by max distance and optional sports overlap, order
    // by distance with NULLs last. `sports` is passed as a comma-joined string
    // to keep parameter binding portable; "" disables the filter.
    @Query(value = """
            WITH venue_distances AS (
                SELECT
                    v.id,
                    v.slug,
                    v.name,
                    v.description,
                    v.image_url,
                    v.address,
                    v.city_id,
                    c.name AS city,
                    c.state_code,
                    c.timezone,
                    v.sports,
                    v.amenities,
                    v.price_cents,
                    CASE
                        WHEN CAST(:lat AS double precision) IS NULL
                          OR CAST(:lon AS double precision) IS NULL
                            THEN NULL::double precision
                        ELSE 6371 * acos(
                            LEAST(1.0,
                                cos(radians(CAST(:lat AS double precision))) * cos(radians(v.latitude))
                                * cos(radians(v.longitude) - radians(CAST(:lon AS double precision)))
                                + sin(radians(CAST(:lat AS double precision))) * sin(radians(v.latitude))
                            )
                        )
                    END AS distance_km
                FROM public.venues v
                JOIN public.cities c ON c.id = v.city_id
                WHERE v.active = true
                  AND (
                      CAST(:sports AS text) = ''
                      OR v.sports && string_to_array(CAST(:sports AS text), ',')
                  )
            )
            SELECT
                vd.id            AS id,
                vd.slug          AS slug,
                vd.name          AS name,
                vd.description   AS description,
                vd.image_url     AS "imageUrl",
                vd.address       AS address,
                vd.city_id       AS "cityId",
                vd.city          AS city,
                vd.state_code    AS "stateCode",
                vd.timezone      AS timezone,
                vd.sports        AS sports,
                vd.amenities::text AS amenities,
                vd.price_cents   AS "priceCents",
                vd.distance_km   AS "distanceKm"
            FROM venue_distances vd
            WHERE vd.distance_km IS NULL
               OR vd.distance_km <= CAST(:maxDistanceKm AS double precision)
            ORDER BY vd.distance_km ASC NULLS LAST, vd.name ASC
            """, nativeQuery = true)
    List<VenueDistanceProjection> findVenuesWithDistance(
            @Param("lat") Double lat,
            @Param("lon") Double lon,
            @Param("maxDistanceKm") double maxDistanceKm,
            @Param("sports") String sports
    );

    @Query(value = """
            SELECT COUNT(*)
            FROM public.bookings b
            JOIN public.courts c ON c.id = b.court_id
            WHERE c.venue_id = :venueId
            """, nativeQuery = true)
    long countBookingsByVenueId(@Param("venueId") UUID venueId);
}
