package com.ciao.backend.pattern.observer.parcel;

import com.ciao.backend.entity.Parcel;

public interface ParcelTrackingObserver {
    void onStatusChanged(Parcel parcel, String previousStatus, String newStatus);
}
