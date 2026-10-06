package com.ciao.backend.pattern.observer.parcel;

import com.ciao.backend.entity.Parcel;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ParcelTrackingSubject {
    private final List<ParcelTrackingObserver> observers;

    public ParcelTrackingSubject(List<ParcelTrackingObserver> observers) {
        this.observers = List.copyOf(observers);
    }

    public void notifyObservers(Parcel parcel, String previousStatus, String newStatus) {
        observers.forEach(observer -> observer.onStatusChanged(parcel, previousStatus, newStatus));
    }
}
