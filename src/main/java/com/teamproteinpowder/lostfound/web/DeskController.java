package com.teamproteinpowder.lostfound.web;

import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.teamproteinpowder.lostfound.domain.*;
import com.teamproteinpowder.lostfound.repo.*;
import com.teamproteinpowder.lostfound.service.*;
import com.teamproteinpowder.lostfound.web.dto.ClaimResponse;

@RestController @RequestMapping("/api/desk") @Transactional(readOnly = true)
public class DeskController {
    private final CurrentUser currentUser;
    private final ClaimService service;
    private final ClaimRepository claims;
    private final ClaimAuditRepository audit;
    public DeskController(CurrentUser currentUser, ClaimService service, ClaimRepository claims, ClaimAuditRepository audit) {
        this.currentUser = currentUser; this.service = service; this.claims = claims; this.audit = audit;
    }
    private User staff(HttpServletRequest http) {
        User user = currentUser.require(http);
        if (user.getRole() != Role.ADMIN) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Campus staff access required");
        return user;
    }
    @GetMapping("/claims")
    public List<ClaimResponse> queue(HttpServletRequest http) {
        staff(http);
        return claims.findForDesk().stream().map(c -> ClaimResponse.summary(c, "STAFF")).toList();
    }
    @GetMapping("/claims/{reference}")
    public ClaimResponse detail(@PathVariable String reference, HttpServletRequest http) {
        staff(http); return ClaimResponse.from(service.getDetail(reference), "STAFF");
    }
    public record Decision(@NotNull Boolean clear, boolean studentIdChecked,
            @NotBlank @Size(min = 10, max = 1000) String note) {}
    @PostMapping("/claims/{reference}/review") @Transactional
    public ClaimResponse review(@PathVariable String reference, @Valid @RequestBody Decision decision, HttpServletRequest http) {
        return ClaimResponse.from(service.deskReview(reference, decision.clear(), decision.studentIdChecked(), decision.note(), staff(http)), "STAFF");
    }
    @GetMapping("/audit")
    public List<ClaimAudit> history(HttpServletRequest http) { staff(http); return audit.findTop500ByOrderByIdDesc(); }
}
