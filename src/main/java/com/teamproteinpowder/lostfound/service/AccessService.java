package com.teamproteinpowder.lostfound.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.teamproteinpowder.lostfound.domain.*;

@Service
public class AccessService {
    public boolean owns(Item item, User user) {
        return user != null && item.getUser() != null && user.getId().equals(item.getUser().getId());
    }

    public void requireOwner(Item item, User user) {
        if (!owns(item, user)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the poster can do this");
    }

    public String role(Claim claim, User user) {
        if (owns(claim.getItem(), user)) return "POSTER";
        if (user != null && claim.getUser() != null && user.getId().equals(claim.getUser().getId())) return "CLAIMANT";
        return null;
    }

    public String requireParticipant(Claim claim, User user) {
        String role = role(claim, user);
        if (role == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This conversation is private");
        return role;
    }
}
