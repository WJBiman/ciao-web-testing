package com.ciao.backend.service;

import com.ciao.backend.dto.RouteRequest;
import com.ciao.backend.entity.Route;
import com.ciao.backend.repository.RouteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class RouteService {

    @Autowired
    private RouteRepository routeRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    public List<Route> getAllRoutes() {
        return routeRepository.findAll();
    }

    public Route createRoute(RouteRequest request) {
        String origin = request.getOrigin() != null ? request.getOrigin().trim() : "";
        String destination = request.getDestination() != null ? request.getDestination().trim() : "";

        if (origin.equalsIgnoreCase(destination)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Route origin and destination cannot be identical.");
        }

        Route route = new Route();
        route.setOrigin(origin);
        route.setDestination(destination);
        route.setBaseFare(request.getBaseFare());
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            route.setStatus(Route.RouteStatus.valueOf(request.getStatus().trim().toUpperCase()));
        } else {
            route.setStatus(Route.RouteStatus.ACTIVE);
        }
        return routeRepository.save(route);
    }

    public Route updateRoute(Integer id, RouteRequest request) {
        Route route = routeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Route not found with id: " + id));

        String origin = request.getOrigin() != null ? request.getOrigin().trim() : "";
        String destination = request.getDestination() != null ? request.getDestination().trim() : "";

        if (origin.equalsIgnoreCase(destination)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Route origin and destination cannot be identical.");
        }

        route.setOrigin(origin);
        route.setDestination(destination);
        route.setBaseFare(request.getBaseFare());
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            route.setStatus(Route.RouteStatus.valueOf(request.getStatus().trim().toUpperCase()));
        }
        return routeRepository.save(route);
    }

    public void deactivateRoute(Integer id) {
        Route route = routeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Route not found with id: " + id));
        route.setStatus(Route.RouteStatus.INACTIVE);
        routeRepository.save(route);
    }

    @org.springframework.transaction.annotation.Transactional
    public void deleteRouteWithDependencies(Integer id) {
        if (!routeRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Route not found with id: " + id);
        }

        // 1. Guard against deleting routes that have passenger reservations or bookings on their schedules
        Integer reservationCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM reservations r JOIN schedules s ON r.schedule_id = s.id WHERE s.route_id = ?",
                Integer.class, id);
        if (reservationCount != null && reservationCount > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot delete route #" + id + " because passengers have reservations on its schedules. Deactivate the route or cancel reservations first.");
        }

        Integer bookingCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM bookings b JOIN schedules s ON b.schedule_id = s.id WHERE s.route_id = ?",
                Integer.class, id);
        if (bookingCount != null && bookingCount > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot delete route #" + id + " because bookings are linked to its scheduled trips.");
        }

        Integer parcelCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM parcels p JOIN schedules s ON p.schedule_id = s.id WHERE s.route_id = ?",
                Integer.class, id);
        if (parcelCount != null && parcelCount > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot delete route #" + id + " because parcels are assigned to its scheduled trips.");
        }

        // 2. Unlink any lost items referencing this route
        jdbc.update("UPDATE lost_items SET route_id = NULL WHERE route_id = ?", id);

        // 3. Clear recurrence parent reference on schedules for this route to prevent foreign key errors
        jdbc.update("UPDATE schedules SET recurrence_parent_id = NULL WHERE route_id = ?", id);

        // 4. Delete unbooked schedules associated with this route so fk_schedules_route constraint is satisfied
        jdbc.update("DELETE FROM schedules WHERE route_id = ?", id);

        // 5. Delete route stops associated with this route
        jdbc.update("DELETE FROM route_stops WHERE route_id = ?", id);

        // 6. Delete the route record from database
        routeRepository.deleteById(id);
        routeRepository.flush();
    }
}

