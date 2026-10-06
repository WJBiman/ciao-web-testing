package com.ciao.backend.pattern.observer.group;

import com.ciao.backend.entity.GroupBooking;
import org.springframework.stereotype.Component;

@Component
public class GroupBookingCustomerAlertObserver implements GroupBookingObserver {
    @Override
    public void onBookingChanged(GroupBooking booking, String eventDescription) {
        System.out.println("[OBSERVER: GROUP-CUSTOMER] Booking #" + booking.getId() + ": " + eventDescription);
    }
}
