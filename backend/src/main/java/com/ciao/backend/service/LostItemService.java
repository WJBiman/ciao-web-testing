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

        item.setReportedBy(eer.currentUser());
        LostItem saved = lostItemRepository.save(item);
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

    public LostItemResponse updateStatus(Integer id, LostItemStatus newStatus) {
        LostItem item = lostItemRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Lost item not found with id: " + id));

        LostItemStatus current = item.getStatus();

        // Enforce valid lifecycle: LOST → FOUND.
        // Direct transition to CLAIMED or RETURNED is disallowed without an approved EER claim decision.
        if (newStatus == LostItemStatus.CLAIMED || newStatus == LostItemStatus.RETURNED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Items cannot be marked as CLAIMED or RETURNED directly. An official ownership claim must be submitted by the passenger and approved by a Customer Service Supervisor.");
        }

        boolean validTransition = (current == LostItemStatus.LOST && newStatus == LostItemStatus.FOUND);

        if (!validTransition) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid status transition from " + current + " to " + newStatus +
                    ". Allowed staff action: LOST → FOUND.");
        }

        item.setStatus(newStatus);
        LostItem updated = lostItemRepository.save(item);
        return toResponse(updated);
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

        return resp;
    }
}
