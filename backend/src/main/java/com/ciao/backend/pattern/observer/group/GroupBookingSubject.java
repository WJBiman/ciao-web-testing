package com.ciao.backend.pattern.observer.group;

import com.ciao.backend.entity.GroupBooking;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GroupBookingSubject {
    private final List<GroupBookingObserver> observers;

    public GroupBookingSubject(List<GroupBookingObserver> observers) {
        this.observers = List.copyOf(observers);
    }

    public void notifyObservers(GroupBooking booking, String eventDescription) {
        observers.forEach(observer -> observer.onBookingChanged(booking, eventDescription));
    }
}
