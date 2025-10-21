package com.vrp.domain;

import java.util.ArrayList;
import java.util.List;

public class Route {
    
    private List<Event> events;
    private long durationSeconds;
    
    public Route() {
        this.events = new ArrayList<>();
        this.durationSeconds = 0L;
    }
    
    public Route(List<Event> events, long durationSeconds) {
        this.events = new ArrayList<>(events);
        this.durationSeconds = durationSeconds;
    }
    
    public List<Event> getEvents() {
        return events;
    }
    
    public void setEvents(List<Event> events) {
        this.events = events;
    }
    
    public long getDurationSeconds() {
        return durationSeconds;
    }
    
    public void setDurationSeconds(long durationSeconds) {
        this.durationSeconds = durationSeconds;
    }
    
    public boolean isEmpty() {
        return events == null || events.isEmpty();
    }
    
    public int size() {
        return events == null ? 0 : events.size();
    }
}
