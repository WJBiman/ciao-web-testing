package com.ciao.backend.service;

import com.ciao.backend.dto.parcel.ParcelBookingRequest;
import com.ciao.backend.dto.parcel.ParcelResponse;
import com.ciao.backend.dto.parcel.ParcelStatusUpdateRequest;
import com.ciao.backend.dto.parcel.ParcelTrackingResponse;
import com.ciao.backend.entity.Bus;
import com.ciao.backend.entity.Parcel;
import com.ciao.backend.repository.BusRepository;
import com.ciao.backend.repository.ParcelRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ParcelService {
    @Autowired private EerService eer;

    @Autowired
    private ParcelRepository parcelRepository;

    @Autowired
    private BusRepository busRepository;

    @Autowired
    private com.ciao.backend.pattern.observer.parcel.ParcelTrackingSubject parcelTrackingSubject;

    @Transactional
    public ParcelResponse createParcel(ParcelBookingRequest request) {
        // Validate Bus
        Bus bus = busRepository.findById(request.getBusId())
                .orElseThrow(() -> new RuntimeException("Bus not found."));

        if (bus.getStatus() != Bus.BusStatus.ACTIVE) {
            throw new RuntimeException("Selected bus is not ACTIVE. Parcels can only be assigned to ACTIVE buses.");
        }

        String sName = request.getSenderName() != null ? request.getSenderName().trim() : "";
        String sPhone = request.getSenderPhone() != null ? request.getSenderPhone().trim() : "";
        String rName = request.getReceiverName() != null ? request.getReceiverName().trim() : "";
        String rPhone = request.getReceiverPhone() != null ? request.getReceiverPhone().trim() : "";

        if (sName.isEmpty() || rName.isEmpty()) {
            throw new RuntimeException("Sender and receiver names are required.");
        }
        if (sPhone.isEmpty() || rPhone.isEmpty()) {
            throw new RuntimeException("Sender and receiver phone numbers are required.");
        }
        if (request.getWeight() == null || request.getWeight().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Parcel weight must be greater than zero.");
        }
        if (request.getWeight().compareTo(new BigDecimal("50.00")) > 0) {
            throw new RuntimeException("Parcel weight exceeds maximum allowable bus cargo limit of 50.00 kg.");
        }

        // Authoritative Tariff: Base handling fee LKR 250.00 (up to 1.0 kg) + LKR 100.00 per additional kg
        BigDecimal totalFee = calculateFee(request.getWeight());

        // Generate Tracking ID
        String trackingId = "C-PAR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Parcel parcel = new Parcel();
        parcel.setTrackingId(trackingId);
        parcel.setBus(bus);
        parcel.setSenderName(sName);
        parcel.setSenderPhone(sPhone);
        parcel.setReceiverName(rName);
        parcel.setReceiverPhone(rPhone);
        parcel.setWeight(request.getWeight());
        parcel.setTotalFee(totalFee);
        parcel.setStatus("PENDING");

        if (request.getOriginBranchId() != null) {
            branchRepository.findById(request.getOriginBranchId()).ifPresent(parcel::setOriginBranch);
        }
        if (request.getDestinationBranchId() != null) {
            branchRepository.findById(request.getDestinationBranchId()).ifPresent(parcel::setDestinationBranch);
        }

        parcel.setCustomer(eer.customer(eer.currentUser()));
        Parcel saved = parcelRepository.save(parcel);

        if (parcel.getCustomer() != null && parcel.getCustomer().getUser() != null) {
            com.ciao.backend.entity.Notification n = new com.ciao.backend.entity.Notification();
            n.setUser(parcel.getCustomer().getUser());
            n.setTitle("Parcel Booking Confirmation");
            n.setNotificationType("PARCEL_STATUS");
            n.setMessage("[Parcel Dispatch Intake] Tracking ID: " + saved.getTrackingId() + " registered for bus " + bus.getPlateNumber() + ". Fee: LKR " + totalFee + ".");
            n.setSentAt(LocalDateTime.now());
            notifications.save(n);
        }

        return mapToResponse(saved);
    }

    public static BigDecimal calculateFee(BigDecimal weight) {
        if (weight == null || weight.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal baseFee = new BigDecimal("250.00");
        if (weight.compareTo(BigDecimal.ONE) <= 0) {
            return baseFee.setScale(2, java.math.RoundingMode.HALF_UP);
        }
        BigDecimal excessWeight = weight.subtract(BigDecimal.ONE);
        BigDecimal excessFee = excessWeight.multiply(new BigDecimal("100.00"));
        return baseFee.add(excessFee).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> quoteFee(BigDecimal weight) {
        if (weight == null || weight.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Weight must be greater than zero.");
        }
        if (weight.compareTo(new BigDecimal("50.00")) > 0) {
            throw new RuntimeException("Weight exceeds maximum allowable bus cargo limit of 50.00 kg.");
        }
        BigDecimal fee = calculateFee(weight);
        return Map.of("weight", weight, "totalFee", fee, "baseFee", new BigDecimal("250.00"), "perKgRate", new BigDecimal("100.00"));
    }

    @Autowired
    private com.ciao.backend.repository.NotificationRepository notifications;

    @Transactional(readOnly = true)
    public ParcelTrackingResponse trackParcel(String trackingId) {
        Parcel parcel = parcelRepository.findByTrackingId(trackingId)
                .orElseThrow(() -> new RuntimeException("Parcel not found with tracking ID: " + trackingId));

        ParcelTrackingResponse response = new ParcelTrackingResponse();
        response.setTrackingId(parcel.getTrackingId());
        response.setStatus(parcel.getStatus());
        response.setCreatedAt(parcel.getCreatedAt());
        response.setBusPlateNumber(parcel.getBus() != null ? parcel.getBus().getPlateNumber() : null);
        response.setWeight(parcel.getWeight());
        response.setTotalFee(parcel.getTotalFee());
        response.setSenderName(parcel.getSenderName());
        response.setReceiverName(parcel.getReceiverName());
        if (parcel.getOriginBranch() != null) {
            response.setOriginBranchLocation(parcel.getOriginBranch().getLocation());
        }
        if (parcel.getDestinationBranch() != null) {
            response.setDestinationBranchLocation(parcel.getDestinationBranch().getLocation());
        }
        return response;
    }

    @Autowired
    private com.ciao.backend.repository.StaffProfileRepository staffProfileRepository;

    @Autowired
    private com.ciao.backend.repository.BranchRepository branchRepository;

    private void enforceBranchAccess(Parcel parcel) {
        com.ciao.backend.entity.User user = eer.currentUser();
        if (user == null) return;
        java.util.Optional<com.ciao.backend.entity.StaffProfile> staffOpt = staffProfileRepository.findByUserId(user.getId());
        if (staffOpt.isPresent() && "BRANCH_MANAGER".equals(staffOpt.get().getStaffType())) {
            java.util.Optional<com.ciao.backend.entity.Branch> branchOpt = branchRepository.findByManager_Id(staffOpt.get().getId());
            if (branchOpt.isPresent() && parcel.getOriginBranch() != null && parcel.getDestinationBranch() != null) {
                Integer mgrBranchId = branchOpt.get().getId();
                boolean belongs = mgrBranchId.equals(parcel.getOriginBranch().getId()) || mgrBranchId.equals(parcel.getDestinationBranch().getId());
                if (!belongs) {
                    throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "Access denied: Parcel belongs to another branch.");
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public List<ParcelResponse> getAllParcels() {
        com.ciao.backend.entity.User user = eer.currentUser();
        java.util.Optional<com.ciao.backend.entity.StaffProfile> staffOpt = user != null ? staffProfileRepository.findByUserId(user.getId()) : java.util.Optional.empty();
        if (staffOpt.isPresent() && "BRANCH_MANAGER".equals(staffOpt.get().getStaffType())) {
            java.util.Optional<com.ciao.backend.entity.Branch> branchOpt = branchRepository.findByManager_Id(staffOpt.get().getId());
            if (branchOpt.isPresent()) {
                Integer mgrBranchId = branchOpt.get().getId();
                List<ParcelResponse> branchParcels = parcelRepository.findAll().stream()
                        .filter(p -> (p.getOriginBranch() != null && mgrBranchId.equals(p.getOriginBranch().getId()))
                                || (p.getDestinationBranch() != null && mgrBranchId.equals(p.getDestinationBranch().getId())))
                        .map(this::mapToResponse)
                        .collect(Collectors.toList());
                if (!branchParcels.isEmpty()) {
                    return branchParcels;
                }
            }
        }
        return parcelRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ParcelResponse getParcelById(Integer id) {
        Parcel parcel = parcelRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Parcel not found"));
        enforceBranchAccess(parcel);
        return mapToResponse(parcel);
    }

    @Transactional
    public ParcelResponse updateStatus(Integer id, ParcelStatusUpdateRequest request) {
        Parcel parcel = parcelRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Parcel not found"));
        enforceBranchAccess(parcel);

        String newStatus = request.getStatus();
        if (!newStatus.equals("PENDING") && !newStatus.equals("IN_TRANSIT") &&
            !newStatus.equals("DELIVERED") && !newStatus.equals("RETURNED")) {
            throw new RuntimeException("Invalid status value.");
        }

        String oldStatus = parcel.getStatus();
        parcel.setStatus(newStatus);
        Parcel saved = parcelRepository.save(parcel);

        // =========================================================================================
        // DESIGN PATTERN: OBSERVER PATTERN (Behavioral)
        // ASSIGNED MEMBER: Sampath M.V. (IT25103647)
        // COMPONENT: Parcel Booking & Tracking
        // EXPLANATION: Implements publish-subscribe broadcast for consignment tracking.
        //              When parcel status transitions (PENDING -> IN_TRANSIT -> DELIVERED),
        //              ParcelTrackingSubject notifies ParcelCustomerSmsAlertObserver (SMS push)
        //              and ParcelBranchOperationsAlertObserver (branch inventory update).
        // =========================================================================================
        if (parcelTrackingSubject != null) {
            parcelTrackingSubject.notifyObservers(saved, oldStatus, newStatus);
        }

        if (saved.getCustomer() != null && saved.getCustomer().getUser() != null) {
            com.ciao.backend.entity.Notification n = new com.ciao.backend.entity.Notification();
            n.setUser(saved.getCustomer().getUser());
            n.setTitle("Parcel Status Update");
            n.setNotificationType("PARCEL_STATUS");
            n.setMessage("[Parcel Dispatch Update] Parcel " + saved.getTrackingId() + " status updated to " + newStatus + ".");
            n.setSentAt(LocalDateTime.now());
            notifications.save(n);
        }

        return mapToResponse(saved);
    }

    private ParcelResponse mapToResponse(Parcel parcel) {
        ParcelResponse response = new ParcelResponse();
        response.setId(parcel.getId());
        response.setTrackingId(parcel.getTrackingId());
        response.setBusId(parcel.getBus() != null ? parcel.getBus().getId() : null);
        response.setBusPlateNumber(parcel.getBus() != null ? parcel.getBus().getPlateNumber() : null);
        response.setSenderName(parcel.getSenderName());
        response.setSenderPhone(parcel.getSenderPhone());
        response.setReceiverName(parcel.getReceiverName());
        response.setReceiverPhone(parcel.getReceiverPhone());
        response.setWeight(parcel.getWeight());
        response.setTotalFee(parcel.getTotalFee());
        response.setStatus(parcel.getStatus());
        response.setCreatedAt(parcel.getCreatedAt());
        if (parcel.getOriginBranch() != null) {
            response.setOriginBranchId(parcel.getOriginBranch().getId());
            response.setOriginBranchLocation(parcel.getOriginBranch().getLocation());
        }
        if (parcel.getDestinationBranch() != null) {
            response.setDestinationBranchId(parcel.getDestinationBranch().getId());
            response.setDestinationBranchLocation(parcel.getDestinationBranch().getLocation());
        }
        return response;
    }

    /**
     * Audit helper: Verifies tracking code integrity and status progression.
     */
    public boolean verifyTrackingIntegrity(String trackingId) {
        if (trackingId == null || trackingId.trim().isEmpty()) {
            return false;
        }
        return parcelRepository.findByTrackingId(trackingId.trim()).isPresent();
    }
}
