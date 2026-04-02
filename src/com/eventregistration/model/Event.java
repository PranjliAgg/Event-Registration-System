package com.eventregistration.model;

import java.time.LocalDate;

public class Event {
    private int eventId;
    private String eventName;
    private LocalDate eventDate;
    private int totalSeats;
    private int availableSeats;
    private LocalDate registrationDeadline;
    private String status;
    private String description;
    private int categoryId;
    private String categoryName;
    private int venueId;
    private String venueName;
    private String venueLocation;
    private double price;

    public Event() {}

    public Event(int eventId, String eventName, LocalDate eventDate,
                 int totalSeats, int availableSeats, LocalDate registrationDeadline,
                 String status, String description, int categoryId, int venueId) {
        this.eventId = eventId;
        this.eventName = eventName;
        this.eventDate = eventDate;
        this.totalSeats = totalSeats;
        this.availableSeats = availableSeats;
        this.registrationDeadline = registrationDeadline;
        this.status = status;
        this.description = description;
        this.categoryId = categoryId;
        this.venueId = venueId;
    }

    // ── Getters & Setters ────────────────────────────────────────
    public int getEventId()                    { return eventId; }
    public void setEventId(int v)              { eventId = v; }
    public String getEventName()               { return eventName; }
    public void setEventName(String v)         { eventName = v; }
    public LocalDate getEventDate()            { return eventDate; }
    public void setEventDate(LocalDate v)      { eventDate = v; }
    public int getTotalSeats()                 { return totalSeats; }
    public void setTotalSeats(int v)           { totalSeats = v; }
    public int getAvailableSeats()             { return availableSeats; }
    public void setAvailableSeats(int v)       { availableSeats = v; }
    public LocalDate getRegistrationDeadline() { return registrationDeadline; }
    public void setRegistrationDeadline(LocalDate v) { registrationDeadline = v; }
    public String getStatus()                  { return status; }
    public void setStatus(String v)            { status = v; }
    public String getDescription()             { return description; }
    public void setDescription(String v)       { description = v; }
    public int getCategoryId()                 { return categoryId; }
    public void setCategoryId(int v)           { categoryId = v; }
    public String getCategoryName()            { return categoryName; }
    public void setCategoryName(String v)      { categoryName = v; }
    public int getVenueId()                    { return venueId; }
    public void setVenueId(int v)              { venueId = v; }
    public String getVenueName()               { return venueName; }
    public void setVenueName(String v)         { venueName = v; }
    public String getVenueLocation()           { return venueLocation; }
    public void setVenueLocation(String v)     { venueLocation = v; }

    public double getPrice()                   { return price; }
    public void setPrice(double v)             { price = v; }

    @Override
    public String toString() {
        return eventName + " (" + eventDate + ")";
    }
}
