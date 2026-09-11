package com.teamproteinpowder.lostfound.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class MessageRequest {

    @NotBlank(message = "Write a message first")
    @Size(max = 2000, message = "Keep the message under 2000 characters")
    private String body;

    /** Which side of the conversation is speaking. */
    private boolean fromPoster;

    /** Optional note attached when declining a claim. */
    @Size(max = 500)
    private String reason;

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public boolean isFromPoster() { return fromPoster; }
    public void setFromPoster(boolean fromPoster) { this.fromPoster = fromPoster; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
