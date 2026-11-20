package com.andrija.homesiloserver.invite;

import com.andrija.homesiloserver.user.User;
import com.andrija.homesiloserver.user.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/** A one-time link to create an account, made by an admin. */
@Entity
@Table(name = "invites")
@Getter
@Setter
@NoArgsConstructor
public class Invite {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** SHA-256 of the link's secret, in hex. The secret itself is never stored. */
    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    /** What the new account will be. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    /** Who it's for, as the admin put it ("For Mum"). */
    @Column(length = 80)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime usedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "used_by")
    private User usedBy;

    public InviteStatus status(LocalDateTime now) {
        if (usedAt != null) return InviteStatus.USED;
        return expiresAt.isAfter(now) ? InviteStatus.PENDING : InviteStatus.EXPIRED;
    }
}
