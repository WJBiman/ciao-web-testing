package com.ciao.backend;

import com.ciao.backend.entity.*;
import com.ciao.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class AnonymousPublicExposureTest {

    @Autowired private MockMvc mvc;
    @Autowired private BranchRepository branchRepository;
    @Autowired private StaffProfileRepository staffProfileRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private RouteRepository routeRepository;
    @Autowired private BusRepository busRepository;
    @Autowired private DriverRepository driverRepository;
    @Autowired private ScheduleRepository scheduleRepository;

    @BeforeEach
    void setupData() {
        Role staffRole = roleRepository.findByRoleName("STAFF").orElseGet(() -> {
            Role r = new Role();
            r.setRoleName("STAFF");
            return roleRepository.save(r);
        });

        User managerUser = new User();
        managerUser.setFullName("Sanduni Manager");
        managerUser.setEmail("sanduni.mgr@ciao.test");
        managerUser.setPhone("0771122334");
        managerUser.setPasswordHash("$2a$10$abcdefghijklmnopqrstuvwxyz123456");
        managerUser.setRole(staffRole);
        managerUser = userRepository.save(managerUser);

        StaffProfile staffProfile = new StaffProfile();
        staffProfile.setUser(managerUser);
        staffProfile.setStaffType("BRANCH_MANAGER");
        staffProfile.setEmployeeCode("STF-9901");
        staffProfile = staffProfileRepository.save(staffProfile);

        Branch branch = new Branch();
        branch.setLocation("Colombo Central Terminal");
        branch.setContactNumber("0112345678");
        branch.setManager(staffProfile);
        branchRepository.save(branch);

        Route route = new Route("Colombo", "Kandy", new BigDecimal("1450.00"), Route.RouteStatus.ACTIVE);
        route.setOverseenBy(staffProfile);
        route = routeRepository.save(route);

        Bus bus = new Bus("ND-7711", 49, "WiFi, AC", Bus.BusStatus.ACTIVE);
        bus.setBusType("SUPER_LUXURY_EXPRESSWAY");
        bus.setRegisteredBy(staffProfile);
        bus = busRepository.save(bus);

        Driver driver = new Driver("Sunil Perera", "DL-99887766", "0779988776", Driver.DriverStatus.AVAILABLE);
        driver = driverRepository.save(driver);

        Schedule schedule = new Schedule(route, bus, driver,
                LocalDateTime.now().plusDays(2),
                LocalDateTime.now().plusDays(2).plusHours(3),
                Schedule.ScheduleStatus.SCHEDULED);
        scheduleRepository.save(schedule);
    }

    @Test
    @DisplayName("Anonymous GET /api/fleet/branches exposes minimal DTO: no manager, no user credentials or phone")
    void testAnonymousBranchesNoManagerExposure() throws Exception {
        mvc.perform(get("/api/fleet/branches").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].location").value("Colombo Central Terminal"))
                .andExpect(jsonPath("$[0].contactNumber").value("0112345678"))
                // Assert sensitive/internal properties are absent
                .andExpect(jsonPath("$[0].manager").doesNotExist())
                .andExpect(jsonPath("$[0].user").doesNotExist())
                .andExpect(jsonPath("$[0].password").doesNotExist())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$[0].employeeCode").doesNotExist())
                .andExpect(jsonPath("$[0].hireDate").doesNotExist());
    }

    @Test
    @DisplayName("Anonymous GET /api/routes does not leak internal overseeing staff profiles")
    void testAnonymousRoutesNoStaffExposure() throws Exception {
        mvc.perform(get("/api/routes").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].origin").value("Colombo"))
                .andExpect(jsonPath("$[0].destination").value("Kandy"))
                // OverseenBy should be ignored in JSON
                .andExpect(jsonPath("$[0].overseenBy").doesNotExist());
    }

    @Test
    @DisplayName("Anonymous GET /api/fleet/buses/active does not expose staff registration records")
    void testAnonymousBusesNoStaffExposure() throws Exception {
        mvc.perform(get("/api/fleet/buses/active").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].plateNumber").value("ND-7711"))
                .andExpect(jsonPath("$[0].registeredBy").doesNotExist());
    }

    @Test
    @DisplayName("Anonymous GET /api/schedules includes effectiveSeatFare and strips sensitive driver license & phone")
    void testAnonymousSchedulesSanitizedAndHasEffectiveFare() throws Exception {
        mvc.perform(get("/api/schedules").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists())
                // Verify server-authoritative effective seat fare is serialized
                .andExpect(jsonPath("$[0].effectiveSeatFare").exists())
                // Super luxury 1450 * 2.0 + 400 toll = 3300.00
                .andExpect(jsonPath("$[0].effectiveSeatFare").value(3300.00))
                // Verify driver license and phone numbers are stripped
                .andExpect(jsonPath("$[0].driver.driverName").value("Sunil Perera"))
                .andExpect(jsonPath("$[0].driver.licenseNumber").doesNotExist())
                .andExpect(jsonPath("$[0].driver.contactNumber").doesNotExist());
    }
}
