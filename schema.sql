-- =============================================================================
-- Ciao (Pvt) Ltd - Bus Ticket Reservation System
-- Database Schema Definition (Full EER Diagram Aligned)
-- Engine: MySQL 8.0+ / MariaDB 10.4+
-- Character Set: utf8mb4 / utf8mb4_general_ci
-- =============================================================================

CREATE DATABASE IF NOT EXISTS ciao_db;
USE ciao_db;

SET FOREIGN_KEY_CHECKS = 0;

-- -----------------------------------------------------------------------------
-- 1. AUTHENTICATION, USERS & PROFILES (EER: USER, CUSTOMER, STAFF & Subtypes)
-- -----------------------------------------------------------------------------

DROP TABLE IF EXISTS user_phones;
DROP TABLE IF EXISTS customer_profiles;
DROP TABLE IF EXISTS staff_profiles;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS roles;

CREATE TABLE roles (
    id INT AUTO_INCREMENT PRIMARY KEY,
    role_name VARCHAR(50) NOT NULL UNIQUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    role_id INT NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    username VARCHAR(255) UNIQUE,
    email VARCHAR(100) UNIQUE,
    phone VARCHAR(15) NOT NULL UNIQUE,
    nic VARCHAR(15) UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Multivalued phone numbers (EER: USER.Phone multivalued attribute)
CREATE TABLE user_phones (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    phone_number VARCHAR(255) NOT NULL,
    CONSTRAINT uq_user_phone UNIQUE (user_id, phone_number),
    CONSTRAINT fk_user_phones_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Customer subtype profile (EER: CUSTOMER)
CREATE TABLE customer_profiles (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL UNIQUE,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    address VARCHAR(255),
    registered_date DATETIME(6),
    CONSTRAINT fk_customer_profiles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Staff subtype profile & 6 EER Staff Roles (EER: STAFF Subtypes)
-- Subtypes: SYSTEM_ADMINISTRATOR, BRANCH_MANAGER, OPERATIONS_MANAGER,
--           FINANCE_MANAGER, E_TICKETING_COORDINATOR, CUSTOMER_SERVICE_SUPERVISOR
CREATE TABLE staff_profiles (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL UNIQUE,
    employee_code VARCHAR(255) UNIQUE,
    hire_date DATE,
    staff_type VARCHAR(255) NOT NULL,
    CONSTRAINT fk_staff_profiles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
-- 2. BRANCH OFFICES (EER: BRANCH & ManagedBy BRANCH_MANAGER)
-- -----------------------------------------------------------------------------

DROP TABLE IF EXISTS branches;

CREATE TABLE branches (
    id INT AUTO_INCREMENT PRIMARY KEY,
    location VARCHAR(255) NOT NULL,
    contact_number VARCHAR(255),
    manager_id INT UNIQUE,
    CONSTRAINT fk_branches_manager FOREIGN KEY (manager_id) REFERENCES staff_profiles(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
-- 3. FLEET & CREW MANAGEMENT (EER: BUS, DRIVER, RegisteredBy OPERATIONS_MANAGER)
-- -----------------------------------------------------------------------------

DROP TABLE IF EXISTS bus_seats;
DROP TABLE IF EXISTS drivers;
DROP TABLE IF EXISTS buses;

CREATE TABLE buses (
    id INT AUTO_INCREMENT PRIMARY KEY,
    plate_number VARCHAR(15) NOT NULL UNIQUE,
    capacity INT NOT NULL,
    bus_type VARCHAR(255),
    amenities VARCHAR(255),
    status ENUM('ACTIVE', 'MAINTENANCE', 'RETIRED') DEFAULT 'ACTIVE',
    registered_by_id INT,
    CONSTRAINT fk_buses_registered_by FOREIGN KEY (registered_by_id) REFERENCES staff_profiles(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bus_seats (
    id INT AUTO_INCREMENT PRIMARY KEY,
    bus_id INT NOT NULL,
    seat_number VARCHAR(255) NOT NULL,
    seat_status VARCHAR(255) NOT NULL DEFAULT 'AVAILABLE',
    CONSTRAINT uq_bus_seat UNIQUE (bus_id, seat_number),
    CONSTRAINT fk_bus_seats_bus FOREIGN KEY (bus_id) REFERENCES buses(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE drivers (
    id INT AUTO_INCREMENT PRIMARY KEY,
    driver_name VARCHAR(100) NOT NULL,
    license_number VARCHAR(50) NOT NULL UNIQUE,
    contact_number VARCHAR(15) NOT NULL,
    status ENUM('AVAILABLE', 'ON_DUTY', 'ON_LEAVE', 'RETIRED') DEFAULT 'AVAILABLE',
    assigned_bus_id INT,
    CONSTRAINT fk_drivers_bus FOREIGN KEY (assigned_bus_id) REFERENCES buses(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
-- 4. ROUTE & STOP MANAGEMENT (EER: ROUTE, STOP, OverseenBy OPERATIONS_MANAGER)
-- -----------------------------------------------------------------------------

DROP TABLE IF EXISTS route_stops;
DROP TABLE IF EXISTS routes;

CREATE TABLE routes (
    id INT AUTO_INCREMENT PRIMARY KEY,
    origin VARCHAR(100) NOT NULL,
    destination VARCHAR(100) NOT NULL,
    base_fare DECIMAL(10, 2) NOT NULL,
    distance_km DECIMAL(8, 2),
    status ENUM('ACTIVE', 'INACTIVE') DEFAULT 'ACTIVE',
    overseen_by_id INT,
    CONSTRAINT fk_routes_overseen_by FOREIGN KEY (overseen_by_id) REFERENCES staff_profiles(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE route_stops (
    id INT AUTO_INCREMENT PRIMARY KEY,
    route_id INT NOT NULL,
    sequence_number INT NOT NULL,
    location_name VARCHAR(255) NOT NULL,
    CONSTRAINT uq_route_stop_sequence UNIQUE (route_id, sequence_number),
    CONSTRAINT fk_route_stops_route FOREIGN KEY (route_id) REFERENCES routes(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
-- 5. SCHEDULES & TRIPS (EER: SCHEDULE / TRIP)
-- -----------------------------------------------------------------------------

DROP TABLE IF EXISTS schedules;

CREATE TABLE schedules (
    id INT AUTO_INCREMENT PRIMARY KEY,
    route_id INT NOT NULL,
    bus_id INT,
    driver_id INT,
    departure_time DATETIME NOT NULL,
    arrival_time DATETIME NOT NULL,
    status ENUM('SCHEDULED', 'IN_TRANSIT', 'COMPLETED', 'CANCELLED') DEFAULT 'SCHEDULED',
    repeat_daily BIT(1) NOT NULL DEFAULT 0,
    repeat_until DATE,
    recurrence_parent_id INT,
    is_charter BIT(1) NOT NULL DEFAULT 0,
    CONSTRAINT fk_schedules_route FOREIGN KEY (route_id) REFERENCES routes(id) ON DELETE RESTRICT,
    CONSTRAINT fk_schedules_bus FOREIGN KEY (bus_id) REFERENCES buses(id) ON DELETE SET NULL,
    CONSTRAINT fk_schedules_driver FOREIGN KEY (driver_id) REFERENCES drivers(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
-- 6. UNIFIED BOOKINGS (EER: BOOKING Parent Entity, INDIVIDUAL, GROUP Subtypes)
-- -----------------------------------------------------------------------------

DROP TABLE IF EXISTS tickets;
DROP TABLE IF EXISTS reserved_seats;
DROP TABLE IF EXISTS payments;
DROP TABLE IF EXISTS group_bookings;
DROP TABLE IF EXISTS reservations;
DROP TABLE IF EXISTS bookings;

-- Common parent Booking entity
CREATE TABLE bookings (
    id INT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT,
    schedule_id INT,
    booking_type VARCHAR(255) NOT NULL, -- 'INDIVIDUAL' or 'GROUP'
    booking_status VARCHAR(255) NOT NULL, -- 'PENDING', 'CONFIRMED', 'CANCELLED'
    booking_date DATETIME(6),
    total_fare DECIMAL(12, 2),
    CONSTRAINT fk_bookings_customer FOREIGN KEY (customer_id) REFERENCES customer_profiles(id) ON DELETE SET NULL,
    CONSTRAINT fk_bookings_schedule FOREIGN KEY (schedule_id) REFERENCES schedules(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Individual Ticket Booking details
CREATE TABLE reservations (
    id INT AUTO_INCREMENT PRIMARY KEY,
    booking_id INT UNIQUE,
    schedule_id INT NOT NULL,
    user_id INT,
    passenger_name VARCHAR(100) NOT NULL,
    passenger_phone VARCHAR(15) NOT NULL,
    total_fare DECIMAL(10, 2) NOT NULL,
    status ENUM('PENDING', 'CONFIRMED', 'CANCELLED') DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reservations_booking FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE,
    CONSTRAINT fk_reservations_schedule FOREIGN KEY (schedule_id) REFERENCES schedules(id) ON DELETE RESTRICT,
    CONSTRAINT fk_reservations_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Group Charter Booking details
CREATE TABLE group_bookings (
    id INT AUTO_INCREMENT PRIMARY KEY,
    booking_id INT UNIQUE,
    customer_name VARCHAR(100) NOT NULL,
    customer_phone VARCHAR(15) NOT NULL,
    event_type VARCHAR(255),
    preferred_bus_type VARCHAR(50),
    journey_details VARCHAR(2000),
    start_date DATETIME NOT NULL,
    end_date DATETIME NOT NULL,
    passenger_count INT NOT NULL,
    total_cost DECIMAL(10, 2) NOT NULL,
    deposit_amount DECIMAL(10, 2) NOT NULL,
    status ENUM('PENDING_REVIEW', 'APPROVED', 'DEPOSIT_PAID', 'COMPLETED', 'CANCELLED') DEFAULT 'PENDING_REVIEW',
    assigned_bus_id INT,
    guest_access_token VARCHAR(128) UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_group_bookings_booking FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE,
    CONSTRAINT fk_group_bookings_bus FOREIGN KEY (assigned_bus_id) REFERENCES buses(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Seat reservation & 10-minute pessimistic seat lock
CREATE TABLE reserved_seats (
    id INT AUTO_INCREMENT PRIMARY KEY,
    reservation_id INT NOT NULL,
    seat_id INT,
    seat_number VARCHAR(5) NOT NULL,
    lock_expires_at DATETIME,
    status ENUM('LOCKED', 'BOOKED') DEFAULT 'LOCKED',
    CONSTRAINT fk_reserved_seats_reservation FOREIGN KEY (reservation_id) REFERENCES reservations(id) ON DELETE CASCADE,
    CONSTRAINT fk_reserved_seats_seat FOREIGN KEY (seat_id) REFERENCES bus_seats(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Digital Ticket Entity & Unique QR Code (EER: TICKET)
CREATE TABLE tickets (
    id INT AUTO_INCREMENT PRIMARY KEY,
    reservation_id INT NOT NULL UNIQUE,
    qr_code VARCHAR(255) NOT NULL UNIQUE,
    seat_range VARCHAR(255),
    issue_date DATETIME(6),
    checked_in_at DATETIME(6),
    CONSTRAINT fk_tickets_reservation FOREIGN KEY (reservation_id) REFERENCES reservations(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Financial Ledger & Finance Manager Verification (EER: PAYMENT)
CREATE TABLE payments (
    id INT AUTO_INCREMENT PRIMARY KEY,
    booking_id INT,
    reservation_id INT,
    amount DECIMAL(10, 2) NOT NULL,
    payment_method ENUM('CARD', 'BANK_TRANSFER', 'CASH'),
    payment_type VARCHAR(255),
    status ENUM('PENDING', 'SUCCESS', 'FAILED', 'REFUNDED') DEFAULT 'PENDING',
    transaction_id VARCHAR(100) UNIQUE,
    verified_by_id INT,
    verified_at DATETIME,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payments_booking FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE SET NULL,
    CONSTRAINT fk_payments_reservation FOREIGN KEY (reservation_id) REFERENCES reservations(id) ON DELETE CASCADE,
    CONSTRAINT fk_payments_verified_by FOREIGN KEY (verified_by_id) REFERENCES staff_profiles(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Cancellation & Simulated Refund Requests
DROP TABLE IF EXISTS cancellation_requests;
CREATE TABLE cancellation_requests (
    id INT AUTO_INCREMENT PRIMARY KEY,
    reservation_id INT NOT NULL,
    requested_by_id INT NOT NULL,
    status ENUM('PENDING', 'APPROVED', 'REJECTED') DEFAULT 'PENDING',
    reason TEXT NOT NULL,
    requested_at DATETIME(6) NOT NULL,
    adjudicated_by_id INT,
    adjudicated_at DATETIME(6),
    adjudication_notes TEXT,
    CONSTRAINT fk_cancellation_reservation FOREIGN KEY (reservation_id) REFERENCES reservations(id) ON DELETE CASCADE,
    CONSTRAINT fk_cancellation_requested_by FOREIGN KEY (requested_by_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_cancellation_adjudicated_by FOREIGN KEY (adjudicated_by_id) REFERENCES staff_profiles(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
-- 7. PARCEL LOGISTICS (EER: PARCEL with Branch & Customer relationships)
-- -----------------------------------------------------------------------------

DROP TABLE IF EXISTS parcels;

CREATE TABLE parcels (
    id INT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT,
    origin_branch_id INT,
    destination_branch_id INT,
    schedule_id INT,
    bus_id INT NOT NULL,
    tracking_id VARCHAR(50) NOT NULL UNIQUE,
    sender_name VARCHAR(100) NOT NULL,
    sender_phone VARCHAR(15) NOT NULL,
    receiver_name VARCHAR(100) NOT NULL,
    receiver_phone VARCHAR(15) NOT NULL,
    weight DECIMAL(5, 2) NOT NULL,
    total_fee DECIMAL(10, 2) NOT NULL,
    status ENUM('PENDING', 'IN_TRANSIT', 'DELIVERED', 'RETURNED') DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_parcels_customer FOREIGN KEY (customer_id) REFERENCES customer_profiles(id) ON DELETE SET NULL,
    CONSTRAINT fk_parcels_origin_branch FOREIGN KEY (origin_branch_id) REFERENCES branches(id) ON DELETE SET NULL,
    CONSTRAINT fk_parcels_dest_branch FOREIGN KEY (destination_branch_id) REFERENCES branches(id) ON DELETE SET NULL,
    CONSTRAINT fk_parcels_schedule FOREIGN KEY (schedule_id) REFERENCES schedules(id) ON DELETE SET NULL,
    CONSTRAINT fk_parcels_bus FOREIGN KEY (bus_id) REFERENCES buses(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
-- 8. LOST & FOUND REGISTRY & CLAIMS (EER: LOST_FOUND_ITEM & ClaimedBy / HandledBy)
-- -----------------------------------------------------------------------------

DROP TABLE IF EXISTS lost_item_claims;
DROP TABLE IF EXISTS lost_items;

CREATE TABLE lost_items (
    id INT AUTO_INCREMENT PRIMARY KEY,
    reported_by_id INT,
    handled_by_id INT,
    bus_id INT,
    route_id INT,
    item_description VARCHAR(255) NOT NULL,
    reported_by_name VARCHAR(100) NOT NULL,
    reported_by_phone VARCHAR(15) NOT NULL,
    status ENUM('LOST', 'FOUND', 'CLAIMED') DEFAULT 'LOST',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_lost_items_reported_by FOREIGN KEY (reported_by_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_lost_items_handled_by FOREIGN KEY (handled_by_id) REFERENCES staff_profiles(id) ON DELETE SET NULL,
    CONSTRAINT fk_lost_items_bus FOREIGN KEY (bus_id) REFERENCES buses(id) ON DELETE SET NULL,
    CONSTRAINT fk_lost_items_route FOREIGN KEY (route_id) REFERENCES routes(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE lost_item_claims (
    id INT AUTO_INCREMENT PRIMARY KEY,
    item_id INT NOT NULL,
    claimant_id INT NOT NULL,
    handled_by_id INT,
    proof_description TEXT,
    claim_date DATETIME(6),
    claim_status VARCHAR(255) NOT NULL DEFAULT 'PENDING',
    CONSTRAINT fk_claims_item FOREIGN KEY (item_id) REFERENCES lost_items(id) ON DELETE CASCADE,
    CONSTRAINT fk_claims_claimant FOREIGN KEY (claimant_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_claims_handled_by FOREIGN KEY (handled_by_id) REFERENCES staff_profiles(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
-- 9. NOTIFICATIONS (EER: NOTIFICATION linked to User & Booking)
-- -----------------------------------------------------------------------------

DROP TABLE IF EXISTS notifications;

CREATE TABLE notifications (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    booking_id INT,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    notification_type VARCHAR(255) NOT NULL,
    read_status BIT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(6),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_notifications_booking FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
-- 10. DEFAULT SEED DATA FOR IMMEDIATE DEMO & EVALUATION
-- -----------------------------------------------------------------------------

-- 10.1 Roles
INSERT INTO roles (id, role_name) VALUES
(1, 'PASSENGER'),
(2, 'STAFF'),
(3, 'ADMIN')
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name);

-- 10.2 Users & Accounts
-- Passwords:
-- Admin (admin@ciao.lk) & Member Accounts: Password@123
-- BCrypt Hash: $2a$10$AA0rnT0INN8lihIhCqT9/eX5weYnKq5LYDL3IuNThoBp3rQTN7pLm (Password@123)
INSERT INTO users (id, role_id, full_name, username, email, phone, nic, password_hash) VALUES
(1, 3, 'System Administrator', 'admin', 'admin@ciao.lk', '0771234567', '200012345678', '$2a$10$AA0rnT0INN8lihIhCqT9/eX5weYnKq5LYDL3IuNThoBp3rQTN7pLm'),
(2, 2, 'Warushawithana J.B.', 'it25100691', 'it25100691@ciao.test', '0709900001', '200112345671', '$2a$10$AA0rnT0INN8lihIhCqT9/eX5weYnKq5LYDL3IuNThoBp3rQTN7pLm'),
(3, 2, 'Jahaas M.J.M.', 'it25102586', 'it25102586@ciao.test', '0709900002', '200112345672', '$2a$10$AA0rnT0INN8lihIhCqT9/eX5weYnKq5LYDL3IuNThoBp3rQTN7pLm'),
(4, 2, 'Govinna G.N.C.', 'it25101627', 'it25101627@ciao.test', '0709900003', '200112345673', '$2a$10$AA0rnT0INN8lihIhCqT9/eX5weYnKq5LYDL3IuNThoBp3rQTN7pLm'),
(5, 2, 'De Silva Y.Y.S.', 'it25101753', 'it25101753@ciao.test', '0709900004', '200112345674', '$2a$10$AA0rnT0INN8lihIhCqT9/eX5weYnKq5LYDL3IuNThoBp3rQTN7pLm'),
(6, 2, 'Sampath M.V.', 'it25103647', 'it25103647@ciao.test', '0709900005', '200112345675', '$2a$10$AA0rnT0INN8lihIhCqT9/eX5weYnKq5LYDL3IuNThoBp3rQTN7pLm'),
(7, 2, 'Dahanayaka T.S.', 'it25103474', 'it25103474@ciao.test', '0709900006', '200112345676', '$2a$10$AA0rnT0INN8lihIhCqT9/eX5weYnKq5LYDL3IuNThoBp3rQTN7pLm'),
(8, 1, 'Verified Passenger Demo', 'passenger', 'passenger@ciao.lk', '0778899001', '199512345678', '$2a$10$AA0rnT0INN8lihIhCqT9/eX5weYnKq5LYDL3IuNThoBp3rQTN7pLm')
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), password_hash = VALUES(password_hash);

-- 10.3 Staff Profiles (6 EER Subtypes)
INSERT INTO staff_profiles (id, user_id, employee_code, hire_date, staff_type) VALUES
(1, 1, 'EMP-SYSADMIN', '2026-01-01', 'SYSTEM_ADMINISTRATOR'),
(2, 2, 'DEMO-IT25100691', '2026-01-15', 'OPERATIONS_MANAGER'),
(3, 3, 'DEMO-IT25102586', '2026-01-15', 'CUSTOMER_SERVICE_SUPERVISOR'),
(4, 4, 'DEMO-IT25101627', '2026-01-15', 'FINANCE_MANAGER'),
(5, 5, 'DEMO-IT25101753', '2026-01-15', 'OPERATIONS_MANAGER'),
(6, 6, 'DEMO-IT25103647', '2026-01-15', 'BRANCH_MANAGER'),
(7, 7, 'DEMO-IT25103474', '2026-01-15', 'E_TICKETING_COORDINATOR')
ON DUPLICATE KEY UPDATE staff_type = VALUES(staff_type), employee_code = VALUES(employee_code);

-- 10.4 Customer Profiles
INSERT INTO customer_profiles (id, user_id, first_name, last_name, address, registered_date) VALUES
(1, 8, 'Verified', 'Passenger', '124 Galle Road, Colombo 03', '2026-08-14 09:00:00')
ON DUPLICATE KEY UPDATE first_name = VALUES(first_name);


-- =============================================================================
-- 1. EXPANDED SRI LANKAN REGIONAL TERMINAL BRANCHES
-- =============================================================================
INSERT INTO branches (id, location, contact_number, manager_id) VALUES
(1, 'Colombo Central Terminal (Bastian Mawatha)', '0112345678', NULL),
(2, 'Makumbura Multimodal Transport Hub (Kottawa)', '0112899123', NULL),
(3, 'Kandy Goods Shed Terminal', '0812345678', NULL),
(4, 'Galle Central Bus Stand', '0912345678', NULL),
(5, 'Matara Expressway Terminal', '0412345678', NULL),
(6, 'Jaffna Central Bus Stand', '0212345678', NULL),
(7, 'Anuradhapura New Town Bus Stand', '0252233445', NULL),
(8, 'Badulla Main Bus Terminal', '0552233445', NULL),
(9, 'Nuwara Eliya Central Bus Stand', '0522233445', NULL),
(10, 'Kurunegala Central Bus Stand', '0372233445', NULL),
(11, 'Trincomalee Central Bus Stand', '0262233445', NULL),
(12, 'Batticaloa Main Bus Terminal', '0652233445', NULL),
(13, 'Hambantota Ruhunu Magampura Terminal', '0472233445', NULL),
(14, 'Ratnapura Clock Tower Terminal', '0452233445', NULL),
(15, 'Negombo Central Bus Stand', '0312233445', NULL)
ON DUPLICATE KEY UPDATE location = VALUES(location), contact_number = VALUES(contact_number);

-- =============================================================================
-- 2. EXPANDED LUXURY INTERCITY BUS FLEET
-- =============================================================================
INSERT INTO buses (id, plate_number, capacity, bus_type, amenities, status, registered_by_id) VALUES
(1, 'ND-4521', 42, 'Super Luxury AC', 'Air Conditioning, WiFi, Reclining Seats, USB Charging Ports, Onboard Audio', 'ACTIVE', 2),
(2, 'ND-5544', 42, 'Super Luxury AC', 'Air Conditioning, High-speed WiFi, Footrests, Cup Holders, USB Power', 'ACTIVE', 2),
(3, 'CAD-1029', 49, 'Luxury AC', 'Air Conditioning, Adjustable Seats, Overhead Parcel Storage, Audio System', 'ACTIVE', 5),
(4, 'NB-8812', 42, 'Super Luxury AC', 'Air Conditioning, WiFi, Panoramic Windows, Air Suspension, USB', 'ACTIVE', 5),
(5, 'WP-ND-7788', 42, 'Super Luxury AC', 'Volvo B11R Multi-Axle, Ambient Cabin Lighting, Individual AC Vents, USB', 'ACTIVE', 2),
(6, 'WP-NC-3344', 49, 'Luxury AC', 'Volvo B9R Intercity Coach, Tinted Double Glazed Windows, Ergonomic Seats', 'ACTIVE', 2),
(7, 'NA-9102', 49, 'Semi-Luxury AC', 'Ashok Leyland Viking AC, Pushback Seats, Overhead Racks, Speed Governor', 'ACTIVE', 5),
(8, 'NA-4455', 42, 'Express AC', 'Ashok Leyland Lynx Smart, Reading Lights, Rapid Dual AC Compressor', 'ACTIVE', 5),
(9, 'WP-ND-9911', 42, 'Super Luxury AC', 'Isuzu Gala Super Luxury, Electronic Air Suspension, Leather Recliner Seats', 'ACTIVE', 2),
(10, 'WP-ND-6622', 49, 'Luxury AC', 'Isuzu Forward Highway Cruiser, Wide View Front Windshield, Luggage Bay', 'ACTIVE', 5),
(11, 'WP-NE-4512', 42, 'Super Luxury AC', 'King Long Longwei II, Individual Climate Controls, Fast USB-C Charging', 'ACTIVE', 2),
(12, 'WP-NE-7819', 49, 'Luxury AC', 'King Long Highway Express, High Deck Passenger Comfort, Curtains', 'ACTIVE', 5),
(13, 'WP-PA-5566', 28, 'Charter VIP Mini', 'Mitsubishi Fuso Rosa VIP, Executive Leather Seats, Refreshment Cooler', 'ACTIVE', 2),
(14, 'WP-PA-8899', 28, 'Charter Excursion Mini', 'Toyota Coaster Luxury, Tour Guide Public Address System, Tinted Glass', 'ACTIVE', 5)
ON DUPLICATE KEY UPDATE bus_type = VALUES(bus_type), amenities = VALUES(amenities), capacity = VALUES(capacity), status = VALUES(status);

-- =============================================================================
-- 3. EXPANDED CERTIFIED DRIVER ROSTER
-- =============================================================================
INSERT INTO drivers (id, driver_name, license_number, contact_number, status, assigned_bus_id) VALUES
(1, 'Sunil Shantha', 'B-1029384', '0772233441', 'AVAILABLE', 1),
(2, 'Nimal Wickramasinghe', 'B-2938471', '0773344552', 'AVAILABLE', 2),
(3, 'Chaminda Bandara', 'B-3847192', '0774455663', 'AVAILABLE', 3),
(4, 'Roshan Gunasekara', 'B-4719283', '0775566774', 'AVAILABLE', 4),
(5, 'Mahinda Kumara', 'B-5829104', '0776677885', 'AVAILABLE', 5),
(6, 'Priyantha Jayasuriya', 'B-6938215', '0777788996', 'AVAILABLE', 6),
(7, 'Anura Senanayake', 'B-7049326', '0778899007', 'AVAILABLE', 7),
(8, 'Dinesh Pushpakumara', 'B-8150437', '0779900118', 'AVAILABLE', 8),
(9, 'Sampath Wijesinghe', 'B-9261548', '0761122339', 'AVAILABLE', 9),
(10, 'Lakshman Rathnayake', 'B-1372659', '0762233440', 'AVAILABLE', 10),
(11, 'Kamal Sirisena', 'B-2483760', '0763344551', 'AVAILABLE', 11),
(12, 'Ruwan Dissanayake', 'B-3594871', '0764455662', 'AVAILABLE', 12),
(13, 'Janaka Alahakoon', 'B-4605982', '0765566773', 'AVAILABLE', 13),
(14, 'Sarath Fonseka', 'B-5716093', '0766677884', 'AVAILABLE', 14),
(15, 'Tharanga Paranavithana', 'B-6827104', '0767788995', 'AVAILABLE', 1)
ON DUPLICATE KEY UPDATE driver_name = VALUES(driver_name), license_number = VALUES(license_number), contact_number = VALUES(contact_number);

-- =============================================================================
-- 4. EXPANDED SRI LANKAN INTER-PROVINCIAL BUS ROUTES
-- =============================================================================
INSERT INTO routes (id, origin, destination, base_fare, distance_km, status, overseen_by_id) VALUES
-- Western <-> Central (Kandy Road & Highway)
(1, 'Colombo', 'Kandy', 1450.00, 115.00, 'ACTIVE', 2),
(4, 'Kandy', 'Colombo', 1450.00, 115.00, 'ACTIVE', 2),

-- Southern Expressway (Colombo <-> Galle / Matara)
(5, 'Colombo', 'Galle', 950.00, 119.00, 'ACTIVE', 5),
(7, 'Galle', 'Colombo', 950.00, 119.00, 'ACTIVE', 5),
(2, 'Colombo', 'Matara', 1150.00, 160.00, 'ACTIVE', 2),
(3, 'Matara', 'Colombo', 1150.00, 160.00, 'ACTIVE', 2),
(8, 'Kadawatha', 'Matara', 1250.00, 155.00, 'ACTIVE', 2),
(9, 'Matara', 'Kadawatha', 1250.00, 155.00, 'ACTIVE', 2),

-- Northern Province (A9 Highway Express)
(6, 'Colombo', 'Jaffna', 2600.00, 396.00, 'ACTIVE', 5),
(10, 'Jaffna', 'Colombo', 2600.00, 396.00, 'ACTIVE', 5),
(11, 'Kandy', 'Jaffna', 2200.00, 310.00, 'ACTIVE', 2),
(12, 'Jaffna', 'Kandy', 2200.00, 310.00, 'ACTIVE', 2),

-- North Central Province (Anuradhapura / Mannar)
(13, 'Colombo', 'Anuradhapura', 1750.00, 206.00, 'ACTIVE', 5),
(14, 'Anuradhapura', 'Colombo', 1750.00, 206.00, 'ACTIVE', 5),
(15, 'Colombo', 'Mannar', 2300.00, 312.00, 'ACTIVE', 5),
(16, 'Mannar', 'Colombo', 2300.00, 312.00, 'ACTIVE', 5),

-- Uva Province (Badulla / Bandarawela via Route 99)
(17, 'Colombo', 'Badulla', 2100.00, 230.00, 'ACTIVE', 2),
(18, 'Badulla', 'Colombo', 2100.00, 230.00, 'ACTIVE', 2),
(19, 'Kandy', 'Badulla', 1350.00, 132.00, 'ACTIVE', 2),
(20, 'Badulla', 'Kandy', 1350.00, 132.00, 'ACTIVE', 2),

-- Central Hill Country (Nuwara Eliya)
(21, 'Colombo', 'Nuwara Eliya', 1950.00, 175.00, 'ACTIVE', 2),
(22, 'Nuwara Eliya', 'Colombo', 1950.00, 175.00, 'ACTIVE', 2),
(23, 'Kandy', 'Nuwara Eliya', 850.00, 78.00, 'ACTIVE', 2),
(24, 'Nuwara Eliya', 'Kandy', 850.00, 78.00, 'ACTIVE', 2),

-- North Western Province (Kurunegala)
(25, 'Colombo', 'Kurunegala', 850.00, 94.00, 'ACTIVE', 5),
(26, 'Kurunegala', 'Colombo', 850.00, 94.00, 'ACTIVE', 5),

-- Eastern Province (Trincomalee & Batticaloa)
(27, 'Colombo', 'Trincomalee', 2250.00, 257.00, 'ACTIVE', 5),
(28, 'Trincomalee', 'Colombo', 2250.00, 257.00, 'ACTIVE', 5),
(29, 'Colombo', 'Batticaloa', 2400.00, 315.00, 'ACTIVE', 5),
(30, 'Batticaloa', 'Colombo', 2400.00, 315.00, 'ACTIVE', 5),

-- Deep South (Hambantota / Kataragama)
(31, 'Colombo', 'Kataragama', 2200.00, 280.00, 'ACTIVE', 2),
(32, 'Kataragama', 'Colombo', 2200.00, 280.00, 'ACTIVE', 2),
(33, 'Galle', 'Matara', 350.00, 45.00, 'ACTIVE', 5),
(34, 'Matara', 'Galle', 350.00, 45.00, 'ACTIVE', 5),

-- Sabaragamuwa Province (Ratnapura)
(35, 'Colombo', 'Ratnapura', 750.00, 101.00, 'ACTIVE', 2),
(36, 'Ratnapura', 'Colombo', 750.00, 101.00, 'ACTIVE', 2),

-- Airport & Coastal Express
(37, 'Colombo', 'Negombo', 450.00, 38.00, 'ACTIVE', 5),
(38, 'Negombo', 'Colombo', 450.00, 38.00, 'ACTIVE', 5),
(39, 'Makumbura', 'Galle', 850.00, 105.00, 'ACTIVE', 2),
(40, 'Galle', 'Makumbura', 850.00, 105.00, 'ACTIVE', 2)
ON DUPLICATE KEY UPDATE origin = VALUES(origin), destination = VALUES(destination), base_fare = VALUES(base_fare), distance_km = VALUES(distance_km);

-- =============================================================================
-- 5. INTERMEDIATE ROUTE HALT STOPS
-- =============================================================================
DELETE FROM route_stops WHERE route_id IN (1, 2, 5, 6, 17, 27);
INSERT INTO route_stops (route_id, sequence_number, location_name) VALUES
-- Route 1: Colombo -> Kandy
(1, 1, 'Colombo Bastian Mawatha Terminal'),
(1, 2, 'Kadawatha Central Interchange'),
(1, 3, 'Nittambuwa Town Hub'),
(1, 4, 'Warakapola Rest Stop'),
(1, 5, 'Kegalle Bus Stand'),
(1, 6, 'Mawanella Bridge'),
(1, 7, 'Peradeniya University Junction'),
(1, 8, 'Kandy Goods Shed Terminal'),

-- Route 2: Colombo -> Matara (Southern Expressway)
(2, 1, 'Colombo Bastian Mawatha'),
(2, 2, 'Makumbura Multimodal Hub'),
(2, 3, 'Dodangoda Interchange'),
(2, 4, 'Kurundugahahetekma Interchange'),
(2, 5, 'Pinnaduwa (Galle) Exit'),
(2, 6, 'Aparekka Exit'),
(2, 7, 'Matara Expressway Terminal'),

-- Route 5: Colombo -> Galle (Expressway)
(5, 1, 'Colombo Bastian Mawatha'),
(5, 2, 'Makumbura Hub'),
(5, 3, 'Gelidiwela (Welipenna) Service Area'),
(5, 4, 'Pinnaduwa (Galle) Interchange'),
(5, 5, 'Galle Central Bus Stand'),

-- Route 6: Colombo -> Jaffna (A9 Highway)
(6, 1, 'Colombo Bastian Mawatha Terminal'),
(6, 2, 'Kurunegala Central Stand'),
(6, 3, 'Dambulla Dedicated Economic Center'),
(6, 4, 'Habarana Junction'),
(6, 5, 'Medawachchiya A9 Checkpoint'),
(6, 6, 'Vavuniya Central Terminal'),
(6, 7, 'Kilinochchi Town Station'),
(6, 8, 'Jaffna Central Bus Stand'),

-- Route 17: Colombo -> Badulla (Route 99)
(17, 1, 'Colombo Fort'),
(17, 2, 'Avissawella Clock Tower'),
(17, 3, 'Ratnapura New Town Stand'),
(17, 4, 'Balangoda Rest Stop'),
(17, 5, 'Beragala Gap'),
(17, 6, 'Haputale Railway Town'),
(17, 7, 'Bandarawela Bus Stand'),
(17, 8, 'Badulla Main Bus Terminal'),

-- Route 27: Colombo -> Trincomalee
(27, 1, 'Colombo Bastian Mawatha'),
(27, 2, 'Kurunegala Town'),
(27, 3, 'Dambulla Transit Hub'),
(27, 4, 'Habarana Forest Junction'),
(27, 5, 'Kantale Sugar Factory Road'),
(27, 6, 'Trincomalee Central Bus Stand');

-- =============================================================================
-- 6. GENERATE SEATS FOR ALL FLEET BUSES (Buses 1 to 14)
-- =============================================================================
-- Insert seats 1..42 / 1..49 / 1..28 dynamically for each bus
INSERT IGNORE INTO bus_seats (bus_id, seat_number, seat_status) VALUES
(1, '1', 'AVAILABLE'),
(1, '2', 'AVAILABLE'),
(1, '3', 'AVAILABLE'),
(1, '4', 'AVAILABLE'),
(1, '5', 'AVAILABLE'),
(1, '6', 'AVAILABLE'),
(1, '7', 'AVAILABLE'),
(1, '8', 'AVAILABLE'),
(1, '9', 'AVAILABLE'),
(1, '10', 'AVAILABLE'),
(1, '11', 'AVAILABLE'),
(1, '12', 'AVAILABLE'),
(1, '13', 'AVAILABLE'),
(1, '14', 'AVAILABLE'),
(1, '15', 'AVAILABLE'),
(1, '16', 'AVAILABLE'),
(1, '17', 'AVAILABLE'),
(1, '18', 'AVAILABLE'),
(1, '19', 'AVAILABLE'),
(1, '20', 'AVAILABLE'),
(1, '21', 'AVAILABLE'),
(1, '22', 'AVAILABLE'),
(1, '23', 'AVAILABLE'),
(1, '24', 'AVAILABLE'),
(1, '25', 'AVAILABLE'),
(1, '26', 'AVAILABLE'),
(1, '27', 'AVAILABLE'),
(1, '28', 'AVAILABLE'),
(1, '29', 'AVAILABLE'),
(1, '30', 'AVAILABLE'),
(1, '31', 'AVAILABLE'),
(1, '32', 'AVAILABLE'),
(1, '33', 'AVAILABLE'),
(1, '34', 'AVAILABLE'),
(1, '35', 'AVAILABLE'),
(1, '36', 'AVAILABLE'),
(1, '37', 'AVAILABLE'),
(1, '38', 'AVAILABLE'),
(1, '39', 'AVAILABLE'),
(1, '40', 'AVAILABLE'),
(1, '41', 'AVAILABLE'),
(1, '42', 'AVAILABLE'),
(2, '1', 'AVAILABLE'),
(2, '2', 'AVAILABLE'),
(2, '3', 'AVAILABLE'),
(2, '4', 'AVAILABLE'),
(2, '5', 'AVAILABLE'),
(2, '6', 'AVAILABLE'),
(2, '7', 'AVAILABLE'),
(2, '8', 'AVAILABLE'),
(2, '9', 'AVAILABLE'),
(2, '10', 'AVAILABLE'),
(2, '11', 'AVAILABLE'),
(2, '12', 'AVAILABLE'),
(2, '13', 'AVAILABLE'),
(2, '14', 'AVAILABLE'),
(2, '15', 'AVAILABLE'),
(2, '16', 'AVAILABLE'),
(2, '17', 'AVAILABLE'),
(2, '18', 'AVAILABLE'),
(2, '19', 'AVAILABLE'),
(2, '20', 'AVAILABLE'),
(2, '21', 'AVAILABLE'),
(2, '22', 'AVAILABLE'),
(2, '23', 'AVAILABLE'),
(2, '24', 'AVAILABLE'),
(2, '25', 'AVAILABLE'),
(2, '26', 'AVAILABLE'),
(2, '27', 'AVAILABLE'),
(2, '28', 'AVAILABLE'),
(2, '29', 'AVAILABLE'),
(2, '30', 'AVAILABLE'),
(2, '31', 'AVAILABLE'),
(2, '32', 'AVAILABLE'),
(2, '33', 'AVAILABLE'),
(2, '34', 'AVAILABLE'),
(2, '35', 'AVAILABLE'),
(2, '36', 'AVAILABLE'),
(2, '37', 'AVAILABLE'),
(2, '38', 'AVAILABLE'),
(2, '39', 'AVAILABLE'),
(2, '40', 'AVAILABLE'),
(2, '41', 'AVAILABLE'),
(2, '42', 'AVAILABLE'),
(3, '1', 'AVAILABLE'),
(3, '2', 'AVAILABLE'),
(3, '3', 'AVAILABLE'),
(3, '4', 'AVAILABLE'),
(3, '5', 'AVAILABLE'),
(3, '6', 'AVAILABLE'),
(3, '7', 'AVAILABLE'),
(3, '8', 'AVAILABLE'),
(3, '9', 'AVAILABLE'),
(3, '10', 'AVAILABLE'),
(3, '11', 'AVAILABLE'),
(3, '12', 'AVAILABLE'),
(3, '13', 'AVAILABLE'),
(3, '14', 'AVAILABLE'),
(3, '15', 'AVAILABLE'),
(3, '16', 'AVAILABLE'),
(3, '17', 'AVAILABLE'),
(3, '18', 'AVAILABLE'),
(3, '19', 'AVAILABLE'),
(3, '20', 'AVAILABLE'),
(3, '21', 'AVAILABLE'),
(3, '22', 'AVAILABLE'),
(3, '23', 'AVAILABLE'),
(3, '24', 'AVAILABLE'),
(3, '25', 'AVAILABLE'),
(3, '26', 'AVAILABLE'),
(3, '27', 'AVAILABLE'),
(3, '28', 'AVAILABLE'),
(3, '29', 'AVAILABLE'),
(3, '30', 'AVAILABLE'),
(3, '31', 'AVAILABLE'),
(3, '32', 'AVAILABLE'),
(3, '33', 'AVAILABLE'),
(3, '34', 'AVAILABLE'),
(3, '35', 'AVAILABLE'),
(3, '36', 'AVAILABLE'),
(3, '37', 'AVAILABLE'),
(3, '38', 'AVAILABLE'),
(3, '39', 'AVAILABLE'),
(3, '40', 'AVAILABLE'),
(3, '41', 'AVAILABLE'),
(3, '42', 'AVAILABLE'),
(3, '43', 'AVAILABLE'),
(3, '44', 'AVAILABLE'),
(3, '45', 'AVAILABLE'),
(3, '46', 'AVAILABLE'),
(3, '47', 'AVAILABLE'),
(3, '48', 'AVAILABLE'),
(3, '49', 'AVAILABLE'),
(4, '1', 'AVAILABLE'),
(4, '2', 'AVAILABLE'),
(4, '3', 'AVAILABLE'),
(4, '4', 'AVAILABLE'),
(4, '5', 'AVAILABLE'),
(4, '6', 'AVAILABLE'),
(4, '7', 'AVAILABLE'),
(4, '8', 'AVAILABLE'),
(4, '9', 'AVAILABLE'),
(4, '10', 'AVAILABLE'),
(4, '11', 'AVAILABLE'),
(4, '12', 'AVAILABLE'),
(4, '13', 'AVAILABLE'),
(4, '14', 'AVAILABLE'),
(4, '15', 'AVAILABLE'),
(4, '16', 'AVAILABLE'),
(4, '17', 'AVAILABLE'),
(4, '18', 'AVAILABLE'),
(4, '19', 'AVAILABLE'),
(4, '20', 'AVAILABLE'),
(4, '21', 'AVAILABLE'),
(4, '22', 'AVAILABLE'),
(4, '23', 'AVAILABLE'),
(4, '24', 'AVAILABLE'),
(4, '25', 'AVAILABLE'),
(4, '26', 'AVAILABLE'),
(4, '27', 'AVAILABLE'),
(4, '28', 'AVAILABLE'),
(4, '29', 'AVAILABLE'),
(4, '30', 'AVAILABLE'),
(4, '31', 'AVAILABLE'),
(4, '32', 'AVAILABLE'),
(4, '33', 'AVAILABLE'),
(4, '34', 'AVAILABLE'),
(4, '35', 'AVAILABLE'),
(4, '36', 'AVAILABLE'),
(4, '37', 'AVAILABLE'),
(4, '38', 'AVAILABLE'),
(4, '39', 'AVAILABLE'),
(4, '40', 'AVAILABLE'),
(4, '41', 'AVAILABLE'),
(4, '42', 'AVAILABLE'),
(5, '1', 'AVAILABLE'),
(5, '2', 'AVAILABLE'),
(5, '3', 'AVAILABLE'),
(5, '4', 'AVAILABLE'),
(5, '5', 'AVAILABLE'),
(5, '6', 'AVAILABLE'),
(5, '7', 'AVAILABLE'),
(5, '8', 'AVAILABLE'),
(5, '9', 'AVAILABLE'),
(5, '10', 'AVAILABLE'),
(5, '11', 'AVAILABLE'),
(5, '12', 'AVAILABLE'),
(5, '13', 'AVAILABLE'),
(5, '14', 'AVAILABLE'),
(5, '15', 'AVAILABLE'),
(5, '16', 'AVAILABLE'),
(5, '17', 'AVAILABLE'),
(5, '18', 'AVAILABLE'),
(5, '19', 'AVAILABLE'),
(5, '20', 'AVAILABLE'),
(5, '21', 'AVAILABLE'),
(5, '22', 'AVAILABLE'),
(5, '23', 'AVAILABLE'),
(5, '24', 'AVAILABLE'),
(5, '25', 'AVAILABLE'),
(5, '26', 'AVAILABLE'),
(5, '27', 'AVAILABLE'),
(5, '28', 'AVAILABLE'),
(5, '29', 'AVAILABLE'),
(5, '30', 'AVAILABLE'),
(5, '31', 'AVAILABLE'),
(5, '32', 'AVAILABLE'),
(5, '33', 'AVAILABLE'),
(5, '34', 'AVAILABLE'),
(5, '35', 'AVAILABLE'),
(5, '36', 'AVAILABLE'),
(5, '37', 'AVAILABLE'),
(5, '38', 'AVAILABLE'),
(5, '39', 'AVAILABLE'),
(5, '40', 'AVAILABLE'),
(5, '41', 'AVAILABLE'),
(5, '42', 'AVAILABLE'),
(6, '1', 'AVAILABLE'),
(6, '2', 'AVAILABLE'),
(6, '3', 'AVAILABLE'),
(6, '4', 'AVAILABLE'),
(6, '5', 'AVAILABLE'),
(6, '6', 'AVAILABLE'),
(6, '7', 'AVAILABLE'),
(6, '8', 'AVAILABLE'),
(6, '9', 'AVAILABLE'),
(6, '10', 'AVAILABLE'),
(6, '11', 'AVAILABLE'),
(6, '12', 'AVAILABLE'),
(6, '13', 'AVAILABLE'),
(6, '14', 'AVAILABLE'),
(6, '15', 'AVAILABLE'),
(6, '16', 'AVAILABLE'),
(6, '17', 'AVAILABLE'),
(6, '18', 'AVAILABLE'),
(6, '19', 'AVAILABLE'),
(6, '20', 'AVAILABLE'),
(6, '21', 'AVAILABLE'),
(6, '22', 'AVAILABLE'),
(6, '23', 'AVAILABLE'),
(6, '24', 'AVAILABLE'),
(6, '25', 'AVAILABLE'),
(6, '26', 'AVAILABLE'),
(6, '27', 'AVAILABLE'),
(6, '28', 'AVAILABLE'),
(6, '29', 'AVAILABLE'),
(6, '30', 'AVAILABLE'),
(6, '31', 'AVAILABLE'),
(6, '32', 'AVAILABLE'),
(6, '33', 'AVAILABLE'),
(6, '34', 'AVAILABLE'),
(6, '35', 'AVAILABLE'),
(6, '36', 'AVAILABLE'),
(6, '37', 'AVAILABLE'),
(6, '38', 'AVAILABLE'),
(6, '39', 'AVAILABLE'),
(6, '40', 'AVAILABLE'),
(6, '41', 'AVAILABLE'),
(6, '42', 'AVAILABLE'),
(6, '43', 'AVAILABLE'),
(6, '44', 'AVAILABLE'),
(6, '45', 'AVAILABLE'),
(6, '46', 'AVAILABLE'),
(6, '47', 'AVAILABLE'),
(6, '48', 'AVAILABLE'),
(6, '49', 'AVAILABLE'),
(7, '1', 'AVAILABLE'),
(7, '2', 'AVAILABLE'),
(7, '3', 'AVAILABLE'),
(7, '4', 'AVAILABLE'),
(7, '5', 'AVAILABLE'),
(7, '6', 'AVAILABLE'),
(7, '7', 'AVAILABLE'),
(7, '8', 'AVAILABLE'),
(7, '9', 'AVAILABLE'),
(7, '10', 'AVAILABLE'),
(7, '11', 'AVAILABLE'),
(7, '12', 'AVAILABLE'),
(7, '13', 'AVAILABLE'),
(7, '14', 'AVAILABLE'),
(7, '15', 'AVAILABLE'),
(7, '16', 'AVAILABLE'),
(7, '17', 'AVAILABLE'),
(7, '18', 'AVAILABLE'),
(7, '19', 'AVAILABLE'),
(7, '20', 'AVAILABLE'),
(7, '21', 'AVAILABLE'),
(7, '22', 'AVAILABLE'),
(7, '23', 'AVAILABLE'),
(7, '24', 'AVAILABLE'),
(7, '25', 'AVAILABLE'),
(7, '26', 'AVAILABLE'),
(7, '27', 'AVAILABLE'),
(7, '28', 'AVAILABLE'),
(7, '29', 'AVAILABLE'),
(7, '30', 'AVAILABLE'),
(7, '31', 'AVAILABLE'),
(7, '32', 'AVAILABLE'),
(7, '33', 'AVAILABLE'),
(7, '34', 'AVAILABLE'),
(7, '35', 'AVAILABLE'),
(7, '36', 'AVAILABLE'),
(7, '37', 'AVAILABLE'),
(7, '38', 'AVAILABLE'),
(7, '39', 'AVAILABLE'),
(7, '40', 'AVAILABLE'),
(7, '41', 'AVAILABLE'),
(7, '42', 'AVAILABLE'),
(7, '43', 'AVAILABLE'),
(7, '44', 'AVAILABLE'),
(7, '45', 'AVAILABLE'),
(7, '46', 'AVAILABLE'),
(7, '47', 'AVAILABLE'),
(7, '48', 'AVAILABLE'),
(7, '49', 'AVAILABLE'),
(8, '1', 'AVAILABLE'),
(8, '2', 'AVAILABLE'),
(8, '3', 'AVAILABLE'),
(8, '4', 'AVAILABLE'),
(8, '5', 'AVAILABLE'),
(8, '6', 'AVAILABLE'),
(8, '7', 'AVAILABLE'),
(8, '8', 'AVAILABLE'),
(8, '9', 'AVAILABLE'),
(8, '10', 'AVAILABLE'),
(8, '11', 'AVAILABLE'),
(8, '12', 'AVAILABLE'),
(8, '13', 'AVAILABLE'),
(8, '14', 'AVAILABLE'),
(8, '15', 'AVAILABLE'),
(8, '16', 'AVAILABLE'),
(8, '17', 'AVAILABLE'),
(8, '18', 'AVAILABLE'),
(8, '19', 'AVAILABLE'),
(8, '20', 'AVAILABLE'),
(8, '21', 'AVAILABLE'),
(8, '22', 'AVAILABLE'),
(8, '23', 'AVAILABLE'),
(8, '24', 'AVAILABLE'),
(8, '25', 'AVAILABLE'),
(8, '26', 'AVAILABLE'),
(8, '27', 'AVAILABLE'),
(8, '28', 'AVAILABLE'),
(8, '29', 'AVAILABLE'),
(8, '30', 'AVAILABLE'),
(8, '31', 'AVAILABLE'),
(8, '32', 'AVAILABLE'),
(8, '33', 'AVAILABLE'),
(8, '34', 'AVAILABLE'),
(8, '35', 'AVAILABLE'),
(8, '36', 'AVAILABLE'),
(8, '37', 'AVAILABLE'),
(8, '38', 'AVAILABLE'),
(8, '39', 'AVAILABLE'),
(8, '40', 'AVAILABLE'),
(8, '41', 'AVAILABLE'),
(8, '42', 'AVAILABLE'),
(9, '1', 'AVAILABLE'),
(9, '2', 'AVAILABLE'),
(9, '3', 'AVAILABLE'),
(9, '4', 'AVAILABLE'),
(9, '5', 'AVAILABLE'),
(9, '6', 'AVAILABLE'),
(9, '7', 'AVAILABLE'),
(9, '8', 'AVAILABLE'),
(9, '9', 'AVAILABLE'),
(9, '10', 'AVAILABLE'),
(9, '11', 'AVAILABLE'),
(9, '12', 'AVAILABLE'),
(9, '13', 'AVAILABLE'),
(9, '14', 'AVAILABLE'),
(9, '15', 'AVAILABLE'),
(9, '16', 'AVAILABLE'),
(9, '17', 'AVAILABLE'),
(9, '18', 'AVAILABLE'),
(9, '19', 'AVAILABLE'),
(9, '20', 'AVAILABLE'),
(9, '21', 'AVAILABLE'),
(9, '22', 'AVAILABLE'),
(9, '23', 'AVAILABLE'),
(9, '24', 'AVAILABLE'),
(9, '25', 'AVAILABLE'),
(9, '26', 'AVAILABLE'),
(9, '27', 'AVAILABLE'),
(9, '28', 'AVAILABLE'),
(9, '29', 'AVAILABLE'),
(9, '30', 'AVAILABLE'),
(9, '31', 'AVAILABLE'),
(9, '32', 'AVAILABLE'),
(9, '33', 'AVAILABLE'),
(9, '34', 'AVAILABLE'),
(9, '35', 'AVAILABLE'),
(9, '36', 'AVAILABLE'),
(9, '37', 'AVAILABLE'),
(9, '38', 'AVAILABLE'),
(9, '39', 'AVAILABLE'),
(9, '40', 'AVAILABLE'),
(9, '41', 'AVAILABLE'),
(9, '42', 'AVAILABLE'),
(10, '1', 'AVAILABLE'),
(10, '2', 'AVAILABLE'),
(10, '3', 'AVAILABLE'),
(10, '4', 'AVAILABLE'),
(10, '5', 'AVAILABLE'),
(10, '6', 'AVAILABLE'),
(10, '7', 'AVAILABLE'),
(10, '8', 'AVAILABLE'),
(10, '9', 'AVAILABLE'),
(10, '10', 'AVAILABLE'),
(10, '11', 'AVAILABLE'),
(10, '12', 'AVAILABLE'),
(10, '13', 'AVAILABLE'),
(10, '14', 'AVAILABLE'),
(10, '15', 'AVAILABLE'),
(10, '16', 'AVAILABLE'),
(10, '17', 'AVAILABLE'),
(10, '18', 'AVAILABLE'),
(10, '19', 'AVAILABLE'),
(10, '20', 'AVAILABLE'),
(10, '21', 'AVAILABLE'),
(10, '22', 'AVAILABLE'),
(10, '23', 'AVAILABLE'),
(10, '24', 'AVAILABLE'),
(10, '25', 'AVAILABLE'),
(10, '26', 'AVAILABLE'),
(10, '27', 'AVAILABLE'),
(10, '28', 'AVAILABLE'),
(10, '29', 'AVAILABLE'),
(10, '30', 'AVAILABLE'),
(10, '31', 'AVAILABLE'),
(10, '32', 'AVAILABLE'),
(10, '33', 'AVAILABLE'),
(10, '34', 'AVAILABLE'),
(10, '35', 'AVAILABLE'),
(10, '36', 'AVAILABLE'),
(10, '37', 'AVAILABLE'),
(10, '38', 'AVAILABLE'),
(10, '39', 'AVAILABLE'),
(10, '40', 'AVAILABLE'),
(10, '41', 'AVAILABLE'),
(10, '42', 'AVAILABLE'),
(10, '43', 'AVAILABLE'),
(10, '44', 'AVAILABLE'),
(10, '45', 'AVAILABLE'),
(10, '46', 'AVAILABLE'),
(10, '47', 'AVAILABLE'),
(10, '48', 'AVAILABLE'),
(10, '49', 'AVAILABLE'),
(11, '1', 'AVAILABLE'),
(11, '2', 'AVAILABLE'),
(11, '3', 'AVAILABLE'),
(11, '4', 'AVAILABLE'),
(11, '5', 'AVAILABLE'),
(11, '6', 'AVAILABLE'),
(11, '7', 'AVAILABLE'),
(11, '8', 'AVAILABLE'),
(11, '9', 'AVAILABLE'),
(11, '10', 'AVAILABLE'),
(11, '11', 'AVAILABLE'),
(11, '12', 'AVAILABLE'),
(11, '13', 'AVAILABLE'),
(11, '14', 'AVAILABLE'),
(11, '15', 'AVAILABLE'),
(11, '16', 'AVAILABLE'),
(11, '17', 'AVAILABLE'),
(11, '18', 'AVAILABLE'),
(11, '19', 'AVAILABLE'),
(11, '20', 'AVAILABLE'),
(11, '21', 'AVAILABLE'),
(11, '22', 'AVAILABLE'),
(11, '23', 'AVAILABLE'),
(11, '24', 'AVAILABLE'),
(11, '25', 'AVAILABLE'),
(11, '26', 'AVAILABLE'),
(11, '27', 'AVAILABLE'),
(11, '28', 'AVAILABLE'),
(11, '29', 'AVAILABLE'),
(11, '30', 'AVAILABLE'),
(11, '31', 'AVAILABLE'),
(11, '32', 'AVAILABLE'),
(11, '33', 'AVAILABLE'),
(11, '34', 'AVAILABLE'),
(11, '35', 'AVAILABLE'),
(11, '36', 'AVAILABLE'),
(11, '37', 'AVAILABLE'),
(11, '38', 'AVAILABLE'),
(11, '39', 'AVAILABLE'),
(11, '40', 'AVAILABLE'),
(11, '41', 'AVAILABLE'),
(11, '42', 'AVAILABLE'),
(12, '1', 'AVAILABLE'),
(12, '2', 'AVAILABLE'),
(12, '3', 'AVAILABLE'),
(12, '4', 'AVAILABLE'),
(12, '5', 'AVAILABLE'),
(12, '6', 'AVAILABLE'),
(12, '7', 'AVAILABLE'),
(12, '8', 'AVAILABLE'),
(12, '9', 'AVAILABLE'),
(12, '10', 'AVAILABLE'),
(12, '11', 'AVAILABLE'),
(12, '12', 'AVAILABLE'),
(12, '13', 'AVAILABLE'),
(12, '14', 'AVAILABLE'),
(12, '15', 'AVAILABLE'),
(12, '16', 'AVAILABLE'),
(12, '17', 'AVAILABLE'),
(12, '18', 'AVAILABLE'),
(12, '19', 'AVAILABLE'),
(12, '20', 'AVAILABLE'),
(12, '21', 'AVAILABLE'),
(12, '22', 'AVAILABLE'),
(12, '23', 'AVAILABLE'),
(12, '24', 'AVAILABLE'),
(12, '25', 'AVAILABLE'),
(12, '26', 'AVAILABLE'),
(12, '27', 'AVAILABLE'),
(12, '28', 'AVAILABLE'),
(12, '29', 'AVAILABLE'),
(12, '30', 'AVAILABLE'),
(12, '31', 'AVAILABLE'),
(12, '32', 'AVAILABLE'),
(12, '33', 'AVAILABLE'),
(12, '34', 'AVAILABLE'),
(12, '35', 'AVAILABLE'),
(12, '36', 'AVAILABLE'),
(12, '37', 'AVAILABLE'),
(12, '38', 'AVAILABLE'),
(12, '39', 'AVAILABLE'),
(12, '40', 'AVAILABLE'),
(12, '41', 'AVAILABLE'),
(12, '42', 'AVAILABLE'),
(12, '43', 'AVAILABLE'),
(12, '44', 'AVAILABLE'),
(12, '45', 'AVAILABLE'),
(12, '46', 'AVAILABLE'),
(12, '47', 'AVAILABLE'),
(12, '48', 'AVAILABLE'),
(12, '49', 'AVAILABLE'),
(13, '1', 'AVAILABLE'),
(13, '2', 'AVAILABLE'),
(13, '3', 'AVAILABLE'),
(13, '4', 'AVAILABLE'),
(13, '5', 'AVAILABLE'),
(13, '6', 'AVAILABLE'),
(13, '7', 'AVAILABLE'),
(13, '8', 'AVAILABLE'),
(13, '9', 'AVAILABLE'),
(13, '10', 'AVAILABLE'),
(13, '11', 'AVAILABLE'),
(13, '12', 'AVAILABLE'),
(13, '13', 'AVAILABLE'),
(13, '14', 'AVAILABLE'),
(13, '15', 'AVAILABLE'),
(13, '16', 'AVAILABLE'),
(13, '17', 'AVAILABLE'),
(13, '18', 'AVAILABLE'),
(13, '19', 'AVAILABLE'),
(13, '20', 'AVAILABLE'),
(13, '21', 'AVAILABLE'),
(13, '22', 'AVAILABLE'),
(13, '23', 'AVAILABLE'),
(13, '24', 'AVAILABLE'),
(13, '25', 'AVAILABLE'),
(13, '26', 'AVAILABLE'),
(13, '27', 'AVAILABLE'),
(13, '28', 'AVAILABLE'),
(14, '1', 'AVAILABLE'),
(14, '2', 'AVAILABLE'),
(14, '3', 'AVAILABLE'),
(14, '4', 'AVAILABLE'),
(14, '5', 'AVAILABLE'),
(14, '6', 'AVAILABLE'),
(14, '7', 'AVAILABLE'),
(14, '8', 'AVAILABLE'),
(14, '9', 'AVAILABLE'),
(14, '10', 'AVAILABLE'),
(14, '11', 'AVAILABLE'),
(14, '12', 'AVAILABLE'),
(14, '13', 'AVAILABLE'),
(14, '14', 'AVAILABLE'),
(14, '15', 'AVAILABLE'),
(14, '16', 'AVAILABLE'),
(14, '17', 'AVAILABLE'),
(14, '18', 'AVAILABLE'),
(14, '19', 'AVAILABLE'),
(14, '20', 'AVAILABLE'),
(14, '21', 'AVAILABLE'),
(14, '22', 'AVAILABLE'),
(14, '23', 'AVAILABLE'),
(14, '24', 'AVAILABLE'),
(14, '25', 'AVAILABLE'),
(14, '26', 'AVAILABLE'),
(14, '27', 'AVAILABLE'),
(14, '28', 'AVAILABLE');

INSERT INTO schedules (id, route_id, bus_id, driver_id, departure_time, arrival_time, status, repeat_daily, repeat_until, recurrence_parent_id, is_charter) VALUES
(101, 1, 1, 1, '2026-09-01 06:00:00', '2026-09-01 09:15:00', 'SCHEDULED', 1, '2027-12-31', NULL, 0),
(102, 1, 2, 2, '2026-09-01 08:30:00', '2026-09-01 11:45:00', 'SCHEDULED', 1, '2027-12-31', NULL, 0),
(103, 1, 5, 5, '2026-09-01 13:00:00', '2026-09-01 16:15:00', 'SCHEDULED', 1, '2027-12-31', NULL, 0),
(104, 1, 9, 9, '2026-09-01 17:30:00', '2026-09-01 20:45:00', 'SCHEDULED', 1, '2027-12-31', NULL, 0),
(105, 4, 1, 1, '2026-09-01 10:30:00', '2026-09-01 13:45:00', 'SCHEDULED', 1, '2027-12-31', NULL, 0),
(106, 4, 2, 2, '2026-09-01 14:00:00', '2026-09-01 17:15:00', 'SCHEDULED', 1, '2027-12-31', NULL, 0),
(107, 4, 5, 5, '2026-09-01 18:00:00', '2026-09-01 21:15:00', 'SCHEDULED', 1, '2027-12-31', NULL, 0),
(108, 5, 3, 3, '2026-09-01 06:30:00', '2026-09-01 08:30:00', 'SCHEDULED', 1, '2027-12-31', NULL, 0),
(109, 5, 4, 4, '2026-09-01 09:00:00', '2026-09-01 11:00:00', 'SCHEDULED', 1, '2027-12-31', NULL, 0),
(110, 5, 6, 6, '2026-09-01 14:30:00', '2026-09-01 16:30:00', 'SCHEDULED', 1, '2027-12-31', NULL, 0),
(111, 5, 11, 11, '2026-09-01 18:30:00', '2026-09-01 20:30:00', 'SCHEDULED', 1, '2027-12-31', NULL, 0),
(112, 7, 3, 3, '2026-09-01 09:30:00', '2026-09-01 11:30:00', 'SCHEDULED', 1, '2027-12-31', NULL, 0),
(113, 7, 4, 4, '2026-09-01 12:30:00', '2026-09-01 14:30:00', 'SCHEDULED', 1, '2027-12-31', NULL, 0),
(114, 7, 6, 6, '2026-09-01 17:30:00', '2026-09-01 19:30:00', 'SCHEDULED', 1, '2027-12-31', NULL, 0),
(115, 2, 7, 7, '2026-09-01 06:15:00', '2026-09-01 08:45:00', 'SCHEDULED', 1, '2027-12-31', NULL, 0),
(116, 2, 8, 8, '2026-09-01 11:30:00', '2026-09-01 14:00:00', 'SCHEDULED', 1, '2027-12-31', NULL, 0),
(117, 2, 10, 10, '2026-09-01 16:00:00', '2026-09-01 18:30:00', 'SCHEDULED', 1, '2027-12-31', NULL, 0),
(118, 3, 7, 7, '2026-09-01 09:30:00', '2026-09-01 12:00:00', 'SCHEDULED', 1, '2027-12-31', NULL, 0),
(119, 3, 8, 8, '2026-09-01 15:00:00', '2026-09-01 17:30:00', 'SCHEDULED', 1, '2027-12-31', NULL, 0),
(120, 6, 11, 11, '2026-09-01 07:00:00', '2026-09-01 14:30:00', 'SCHEDULED', 1, '2027-12-31', NULL, 0),
(121, 6, 12, 12, '2026-09-01 21:00:00', '2026-09-02 04:30:00', 'SCHEDULED', 1, '2027-12-31', NULL, 0),
(122, 10, 11, 11, '2026-09-01 08:00:00', '2026-09-01 15:30:00', 'SCHEDULED', 1, '2027-12-31', NULL, 0),
(123, 10, 12, 12, '2026-09-01 21:30:00', '2026-09-02 05:00:00', 'SCHEDULED', 1, '2027-12-31', NULL, 0),
(124, 13, 8, 8, '2026-09-01 06:45:00', '2026-09-01 11:15:00', 'SCHEDULED', 1, '2027-12-31', NULL, 0),
(125, 13, 10, 10, '2026-09-01 13:30:00', '2026-09-01 18:00:00', 'SCHEDULED', 1, '2027-12-31', NULL, 0),
(126, 14, 8, 8, '2026-09-01 12:30:00', '2026-09-01 17:00:00', 'SCHEDULED', 1, '2027-12-31', NULL, 0),
(127, 17, 5, 5, '2026-09-01 06:00:00', '2026-09-01 12:30:00', 'SCHEDULED', 1, '2027-12-31', NULL, 0),
(128, 17, 9, 9, '2026-09-01 21:15:00', '2026-09-02 03:45:00', 'SCHEDULED', 1, '2027-12-31', NULL, 0),
(129, 18, 5, 5, '2026-09-01 07:00:00', '2026-09-01 13:30:00', 'SCHEDULED', 1, '2027-12-31', NULL, 0),
(130, 21, 2, 2, '2026-09-01 06:30:00', '2026-09-01 12:00:00', 'SCHEDULED', 1, '2027-12-31', NULL, 0),
(131, 22, 2, 2, '2026-09-01 13:30:00', '2026-09-01 19:00:00', 'SCHEDULED', 1, '2027-12-31', NULL, 0),
(132, 25, 7, 7, '2026-09-01 07:15:00', '2026-09-01 09:30:00', 'SCHEDULED', 1, '2027-12-31', NULL, 0),
(133, 26, 7, 7, '2026-09-01 10:30:00', '2026-09-01 12:45:00', 'SCHEDULED', 1, '2027-12-31', NULL, 0),
(134, 27, 12, 12, '2026-09-01 06:00:00', '2026-09-01 12:15:00', 'SCHEDULED', 1, '2027-12-31', NULL, 0),
(135, 28, 12, 12, '2026-09-01 14:00:00', '2026-09-01 20:15:00', 'SCHEDULED', 1, '2027-12-31', NULL, 0),
(136, 29, 6, 6, '2026-09-01 07:30:00', '2026-09-01 15:00:00', 'SCHEDULED', 1, '2027-12-31', NULL, 0),
(137, 30, 6, 6, '2026-09-01 16:30:00', '2026-09-02 00:00:00', 'SCHEDULED', 1, '2027-12-31', NULL, 0),
(138, 31, 4, 4, '2026-09-01 06:00:00', '2026-09-01 12:30:00', 'SCHEDULED', 1, '2027-12-31', NULL, 0),
(139, 32, 4, 4, '2026-09-01 14:00:00', '2026-09-01 20:30:00', 'SCHEDULED', 1, '2027-12-31', NULL, 0),
(140, 37, 8, 8, '2026-09-01 06:00:00', '2026-09-01 06:50:00', 'SCHEDULED', 1, '2027-12-31', NULL, 0),
(141, 37, 8, 8, '2026-09-01 10:00:00', '2026-09-01 10:50:00', 'SCHEDULED', 1, '2027-12-31', NULL, 0),
(142, 37, 8, 8, '2026-09-01 15:00:00', '2026-09-01 15:50:00', 'SCHEDULED', 1, '2027-12-31', NULL, 0),
(143, 38, 8, 8, '2026-09-01 07:30:00', '2026-09-01 08:20:00', 'SCHEDULED', 1, '2027-12-31', NULL, 0)
ON DUPLICATE KEY UPDATE route_id = VALUES(route_id), bus_id = VALUES(bus_id), driver_id = VALUES(driver_id), status = VALUES(status), repeat_daily = VALUES(repeat_daily), repeat_until = VALUES(repeat_until);

SET FOREIGN_KEY_CHECKS = 1;
