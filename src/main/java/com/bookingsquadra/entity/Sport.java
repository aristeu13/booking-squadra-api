package com.bookingsquadra.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public enum Sport {
    SOCCER("soccer"),
    FUTSAL("futsal"),
    PADEL("padel"),
    BEACH_TENNIS("beach_tennis"),
    TENNIS("tennis"),
    VOLLEYBALL("volleyball"),
    BEACH_VOLLEYBALL("beach_volleyball"),
    FOOTVOLLEY("footvolley"),
    BASKETBALL("basketball"),
    HANDBALL("handball");

    private static final Map<String, Sport> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(Sport::code, s -> s));

    private final String code;

    Sport(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static Sport fromCode(String raw) {
        if (raw == null) return null;
        Sport sport = BY_CODE.get(raw.toLowerCase(Locale.ROOT));
        if (sport == null) {
            throw new IllegalArgumentException("Unknown sport: " + raw);
        }
        return sport;
    }

    public static Sport fromCodeOrNull(String raw) {
        if (raw == null) return null;
        return BY_CODE.get(raw.toLowerCase(Locale.ROOT));
    }
}
