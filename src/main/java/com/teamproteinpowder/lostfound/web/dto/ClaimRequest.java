package com.teamproteinpowder.lostfound.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** What the claim form sends. */
public class ClaimRequest {

    @NotBlank(message = "Tell us your name")
    @Size(max = 80, message = "Keep the name under 80 characters")
    private String claimantName;

    @NotBlank(message = "We need an email address to reach you")
    @Email(message = "That does not look like a valid email address")
    @Size(max = 160)
    private String claimantEmail;

    /* The whole flow hinges on this field, so it has a real minimum length:
       "it's mine" is not something only the owner could know. */
    @NotBlank(message = "Describe something only the owner would know")
    @Size(min = 20, max = 2000, message = "Give at least 20 characters of detail")
    private String proof;

    public String getClaimantName() { return claimantName; }
    public void setClaimantName(String claimantName) { this.claimantName = claimantName; }

    public String getClaimantEmail() { return claimantEmail; }
    public void setClaimantEmail(String claimantEmail) { this.claimantEmail = claimantEmail; }

    public String getProof() { return proof; }
    public void setProof(String proof) { this.proof = proof; }
}
