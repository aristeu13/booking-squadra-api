package com.bookingsquadra.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public enum Amenity {
    // Attendee Comfort & Experience
    PARKING("parking"),
    AIR_CONDITIONING("air_conditioning"),
    ACCESSIBILITY("accessibility"),
    WIFI("wifi"),
    BAR("bar"),
    RESTAURANT("restaurant"),

    // Structure & Seating
    COVERED("covered"), // Important differentiator from open-air venues
    BLEACHERS("bleachers"), // Indicates spectator seating exists

    // Team / Performer Facilities
    LOCKER_ROOM("locker_room"),
    SHOWER("shower"),

    // Technical / Production
    LIGHTING("lighting"); // Note: Assume this means "Event/Broadcast Lighting", not just lightbulbs.

    private static final Map<String, Amenity> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(Amenity::code, a -> a));

    private final String code;

    Amenity(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static Amenity fromCode(String raw) {
        if (raw == null)
            return null;
        Amenity amenity = BY_CODE.get(normalize(raw));
        if (amenity == null) {
            throw new IllegalArgumentException("Unknown amenity: " + raw);
        }
        return amenity;
    }

    public static Amenity fromCodeOrNull(String raw) {
        if (raw == null)
            return null;
        return BY_CODE.get(normalize(raw));
    }

    // Accept legacy camelCase keys (e.g. "lockerRoom") by converting them to
    // snake_case before lookup. Existing rows store amenities as a JSONB object
    // whose keys may not match the canonical enum codes.
    private static String normalize(String raw) {
        StringBuilder out = new StringBuilder(raw.length() + 4);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0)
                    out.append('_');
                out.append(Character.toLowerCase(c));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
