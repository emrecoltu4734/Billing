package com.ispbilling.security;

import jakarta.persistence.*;

/** Panel kullanıcısı (İSS personeli). */
@Entity
@Table(name = "app_user")
public class AppUser {

    public enum Role { ADMIN, STAFF }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;
    private String passwordHash;
    private String fullName;

    @Enumerated(EnumType.STRING)
    private Role role;

    private boolean active = true;

    protected AppUser() {
    }

    public AppUser(String username, String passwordHash, String fullName, Role role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public Role getRole() { return role; }
    public boolean isActive() { return active; }
}
