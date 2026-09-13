package com.teamproteinpowder.lostfound.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CommentRequest {

    @NotBlank(message = "Tell us your name")
    @Size(max = 80, message = "Keep the name under 80 characters")
    private String authorName;

    @Email(message = "That does not look like a valid email address")
    @Size(max = 160)
    private String authorEmail;

    @NotBlank(message = "Write something first")
    @Size(max = 1000, message = "Keep the comment under 1000 characters")
    private String body;

    private boolean privateMessage = false;

    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }

    public String getAuthorEmail() { return authorEmail; }
    public void setAuthorEmail(String authorEmail) { this.authorEmail = authorEmail; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public boolean isPrivateMessage() { return privateMessage; }
    public void setPrivateMessage(boolean privateMessage) { this.privateMessage = privateMessage; }
}
