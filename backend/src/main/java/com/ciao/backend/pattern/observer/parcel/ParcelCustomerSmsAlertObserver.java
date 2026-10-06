package com.ciao.backend.pattern.observer.parcel;

import com.ciao.backend.entity.Parcel;
import org.springframework.stereotype.Component;

@Component
public class ParcelCustomerSmsAlertObserver implements ParcelTrackingObserver {
    @Override
    public void onStatusChanged(Parcel parcel, String previousStatus, String newStatus) {
        System.out.println("[OBSERVER: PARCEL-CUSTOMER] " + parcel.getTrackingId()
                + " changed from " + previousStatus + " to " + newStatus);
    }
}
