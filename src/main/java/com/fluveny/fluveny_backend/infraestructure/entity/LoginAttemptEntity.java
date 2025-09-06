package com.fluveny.fluveny_backend.infraestructure.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "fl_login_attempts")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LoginAttemptEntity {

    @Id
    private String id;

    @Indexed
    private String username;

    @Indexed
    private String ipAddress;

    private int failedAttempts;

    private LocalDateTime lastAttemptTime;

    private LocalDateTime lockedUntil;

    private boolean requiresCaptcha;

    public boolean isLocked() {
        return lockedUntil != null && LocalDateTime.now().isBefore(lockedUntil);
    }

    public boolean shouldRequireCaptcha() {
        return failedAttempts >= 3 || requiresCaptcha;
    }

    public void incrementFailedAttempts() {
        this.failedAttempts++;
        this.lastAttemptTime = LocalDateTime.now();

        if (this.failedAttempts >= 3) {
            this.requiresCaptcha = true;
        }

        if (this.failedAttempts >= 5) {
            this.lockedUntil = LocalDateTime.now().plusMinutes(15);
        }
    }

    public void resetAttempts() {
        this.failedAttempts = 0;
        this.requiresCaptcha = false;
        this.lockedUntil = null;
        this.lastAttemptTime = LocalDateTime.now();
    }
}