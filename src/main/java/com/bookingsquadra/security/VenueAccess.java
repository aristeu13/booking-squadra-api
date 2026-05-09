package com.bookingsquadra.security;

import com.bookingsquadra.repository.VenueOwnerRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("venueAccess")
public class VenueAccess {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final VenueOwnerRepository venueOwnerRepository;

    public VenueAccess(VenueOwnerRepository venueOwnerRepository) {
        this.venueOwnerRepository = venueOwnerRepository;
    }

    public boolean canManage(UUID venueId) {
        if (venueId == null) {
            return false;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        for (GrantedAuthority authority : auth.getAuthorities()) {
            if (ROLE_ADMIN.equals(authority.getAuthority())) {
                return true;
            }
        }
        UUID userId;
        try {
            userId = UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            return false;
        }
        return venueOwnerRepository.existsByUserIdAndVenueId(userId, venueId);
    }
}
