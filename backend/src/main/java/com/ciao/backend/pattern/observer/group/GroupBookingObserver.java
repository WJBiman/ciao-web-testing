package com.ciao.backend.pattern.observer.group;

import com.ciao.backend.entity.GroupBooking;

public interface GroupBookingObserver {
    void onBookingChanged(GroupBooking booking, String eventDescription);
}
