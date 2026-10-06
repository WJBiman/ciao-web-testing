package com.ciao.backend.service;

import com.ciao.backend.dto.LostItemRequest;
import com.ciao.backend.dto.LostItemResponse;
import com.ciao.backend.entity.Bus;
import com.ciao.backend.entity.LostItem;
import com.ciao.backend.entity.LostItemStatus;
import com.ciao.backend.entity.Route;
import com.ciao.backend.repository.BusRepository;
import com.ciao.backend.repository.LostItemRepository;
import com.ciao.backend.repository.RouteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class LostItemService {
    @Autowired private EerService eer;

    @Autowired
    private LostItemRepository lostItemRepository;

    @Autowired
    private BusRepository busRepository;

    @Autowired
    private RouteRepository routeRepository;

    @Autowired
    private com.ciao.backend.repository.UserRepository userRepository;

    @Autowired
    private com.ciao.backend.repository.NotificationRepository notificationRepository;

    @Autowired
    private com.ciao.backend.repository.StaffProfileRepository staffProfileRepository;

    @Autowired
    private com.ciao.backend.repository.LostItemClaimRepository lostItemClaimRepository;

    public LostItemResponse reportLostItem(LostItemRequest request) {
        LostItem item = new LostItem();
        item.setItemDescription(request.getItemDescription().trim());
        item.setReportedByName(request.getReportedByName().trim());
        item.setReportedByPhone(request.getReportedByPhone().trim());
        item.setStatus(LostItemStatus.LOST);

        // Validate and associate bus if provided (nullable in schema)
        if (request.getBusId() != null) {
            Bus bus = busRepository.findById(request.getBusId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Bus not found with id: " + request.getBusId()));
            item.setBus(bus);
        }

        // Validate and associate route if provided (nullable in schema)
        if (request.getRouteId() != null) {
            Route route = routeRepository.findById(request.getRouteId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Route not found with id: " + request.getRouteId()));
            item.setRoute(route);
        }

        // Resolve reporting user (via session, phone, or email)
        com.ciao.backend.entity.User user = eer.currentUser();
        if (user == null) {
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                String name = auth.getName();
                if (name != null && !name.isBlank()) {
                    user = userRepository.findByEmailIgnoreCase(name)
                            .or(() -> userRepository.findByUsernameIgnoreCase(name))
                            .or(() -> userRepository.findByPhone(name))
                            .orElse(null);
                }
            }
        }
        if (user == null && request.getReportedByPhone() != null && !request.getReportedByPhone().isBlank()) {
            String normalizedPhone = com.ciao.backend.security.AccountIdentifiers.normalize(request.getReportedByPhone());
            var matches = userRepository.findByPhoneIn(com.ciao.backend.security.AccountIdentifiers.phoneForms(normalizedPhone));
            if (matches.size() == 1) {
                user = matches.get(0);
            }
        }

        item.setReportedBy(user);
        LostItem saved = lostItemRepository.save(item);

        // Notify reporting passenger
        if (user != null) {
            com.ciao.backend.entity.Notification n = new com.ciao.backend.entity.Notification();
            n.setUser(user);
            n.setTitle("Lost Item Report Logged");
            n.setNotificationType("LOST_ITEM_REPORTED");
            n.setMessage("[Case Docket LF-#" + String.format("%04d", saved.getId()) + " / Item #" + saved.getId() + "] Your lost item report for '" + saved.getItemDescription() + "' has been logged into the terminal registry. If located by staff, you will receive an immediate notification.");
            n.setSentAt(java.time.LocalDateTime.now());
            notificationRepository.save(n);
        }

        // Notify Customer Service Supervisors
        List<com.ciao.backend.entity.StaffProfile> supervisors = staffProfileRepository.findByStaffType("CUSTOMER_SERVICE_SUPERVISOR");
        for (com.ciao.backend.entity.StaffProfile sp : supervisors) {
            if (sp.getUser() != null) {
                com.ciao.backend.entity.Notification sn = new com.ciao.backend.entity.Notification();
                sn.setUser(sp.getUser());
                sn.setTitle("New Lost Item Report");
                sn.setNotificationType("LOST_ITEM_REPORTED");
                sn.setMessage("[New Lost Item #" + saved.getId() + "] Passenger " + saved.getReportedByName() + " reported a lost item: '" + saved.getItemDescription() + "'.");
                sn.setSentAt(java.time.LocalDateTime.now());
                notificationRepository.save(sn);
            }
        }

        return toResponse(saved);
    }

    public List<LostItemResponse> getAllItems(String search, LostItemStatus statusFilter) {
        List<LostItem> items;

        if (search != null && !search.isBlank()) {
            items = lostItemRepository.searchByDescription(search.trim());
        } else if (statusFilter != null) {
            items = lostItemRepository.findByStatus(statusFilter);
        } else {
            items = lostItemRepository.findAllByOrderByCreatedAtDesc();
        }

        return items.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @org.springframework.transaction.annotation.Transactional
    public LostItemResponse updateStatus(Integer id, LostItemStatus newStatus) {
        LostItem item = lostItemRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Lost item not found with id: " + id));

        LostItemStatus current = item.getStatus();

        if (current == LostItemStatus.LOST && newStatus == LostItemStatus.FOUND) {
            item.setStatus(LostItemStatus.FOUND);
            
            // Resolve reporting user if not already linked
            if (item.getReportedBy() == null && item.getReportedByPhone() != null && !item.getReportedByPhone().isBlank()) {
                String normalizedPhone = com.ciao.backend.security.AccountIdentifiers.normalize(item.getReportedByPhone());
                var matches = userRepository.findByPhoneIn(com.ciao.backend.security.AccountIdentifiers.phoneForms(normalizedPhone));
                if (matches.size() == 1) {
                    item.setReportedBy(matches.get(0));
                }
            }

            LostItem updated = lostItemRepository.save(item);

            // Notify user with Item ID
            if (updated.getReportedBy() != null) {
                com.ciao.backend.entity.Notification n = new com.ciao.backend.entity.Notification();
                n.setUser(updated.getReportedBy());
                n.setTitle("Lost Item Located & Secured!");
                n.setNotificationType("LOST_ITEM_FOUND");
                n.setMessage("[Good News! Item #" + updated.getId() + " Found] Your reported item '" + updated.getItemDescription() + "' has been located and secured in terminal custody. Please submit an ownership claim using Item ID #" + updated.getId() + " to initiate handover verification.");
                n.setSentAt(java.time.LocalDateTime.now());
                notificationRepository.save(n);
            }

            return toResponse(updated);
        }

        if (current == LostItemStatus.FOUND && (newStatus == LostItemStatus.CLAIMED || newStatus == LostItemStatus.RETURNED)) {
            // Find claims for this item
            var claims = lostItemClaimRepository.findByItemId(item.getId());
            var approvedClaim = claims.stream().filter(c -> "APPROVED".equalsIgnoreCase(c.getClaimStatus())).findFirst();
            var pendingClaim = claims.stream().filter(c -> "PENDING".equalsIgnoreCase(c.getClaimStatus())).findFirst();

            if (approvedClaim.isEmpty()) {
                if (pendingClaim.isPresent()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Cannot discharge item: An ownership claim (Claim #" + pendingClaim.get().getId() + ") has been submitted by customer but is still PENDING supervisor verification. The Customer Service Supervisor must approve the claim before discharging.");
                } else {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Cannot discharge item: No verified ownership claim found. The passenger must submit an ownership claim and the Customer Service Supervisor must review and approve it before physical discharge.");
                }
            }

            var c = approvedClaim.get();
            c.setClaimStatus("RETURNED");
            c.setReturnedAt(java.time.LocalDateTime.now());
            lostItemClaimRepository.save(c);

            if (c.getClaimant() != null) {
                com.ciao.backend.entity.Notification n = new com.ciao.backend.entity.Notification();
                n.setUser(c.getClaimant());
                n.setTitle("Lost Item Handover Complete");
                n.setNotificationType("CLAIM_UPDATE");
                n.setMessage("[Item Discharged] Your claim #" + c.getId() + " for item #" + item.getId() + " (" + item.getItemDescription() + ") has been discharged and property handed over to you.");
                n.setSentAt(java.time.LocalDateTime.now());
                notificationRepository.save(n);
            }

            item.setStatus(newStatus);
            LostItem updated = lostItemRepository.save(item);
            return toResponse(updated);
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Invalid status transition from " + current + " to " + newStatus + ".");
    }

    private LostItemResponse toResponse(LostItem item) {
        LostItemResponse resp = new LostItemResponse();
        resp.setId(item.getId());
        resp.setItemDescription(item.getItemDescription());
        resp.setReportedByName(item.getReportedByName());
        resp.setReportedByPhone(item.getReportedByPhone());
        resp.setStatus(item.getStatus());
        resp.setCreatedAt(item.getCreatedAt());

        if (item.getBus() != null) {
            resp.setBusId(item.getBus().getId());
            resp.setBusPlateNumber(item.getBus().getPlateNumber());
        }
        if (item.getRoute() != null) {
            resp.setRouteId(item.getRoute().getId());
            resp.setRouteName(item.getRoute().getOrigin() + " → " + item.getRoute().getDestination());
        }

        // Attach Claim verification info
        var itemClaims = lostItemClaimRepository.findByItemId(item.getId());
        var approved = itemClaims.stream().filter(c -> "APPROVED".equalsIgnoreCase(c.getClaimStatus())).findFirst();
        var pendingCount = (int) itemClaims.stream().filter(c -> "PENDING".equalsIgnoreCase(c.getClaimStatus())).count();

        resp.setPendingClaimsCount(pendingCount);
        if (approved.isPresent()) {
            resp.setHasApprovedClaim(true);
            resp.setApprovedClaimId(approved.get().getId());
        } else {
            resp.setHasApprovedClaim(false);
        }

        return resp;
    }
}
