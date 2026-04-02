package com.eventregistration.model;

// ── User ──────────────────────────────────────────────────────
public class User {
    private int userId;
    private String name;
    private String email;
    private String phone;
    private String password;
    private String role;

    public User() {}
    public User(int userId, String name, String email, String phone, String role) {
        this.userId = userId; this.name = name; this.email = email;
        this.phone = phone;   this.role = role;
    }

    public int getUserId()          { return userId; }
    public void setUserId(int v)    { userId = v; }
    public String getName()         { return name; }
    public void setName(String v)   { name = v; }
    public String getEmail()        { return email; }
    public void setEmail(String v)  { email = v; }
    public String getPhone()        { return phone; }
    public void setPhone(String v)  { phone = v; }
    public String getPassword()     { return password; }
    public void setPassword(String v){ password = v; }
    public String getRole()         { return role; }
    public void setRole(String v)   { role = v; }

    @Override public String toString() { return name + " (" + email + ")"; }
}
