package com.ciao.backend.config;

import com.ciao.backend.entity.*;
import com.ciao.backend.repository.*;
import com.ciao.backend.service.EerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Explicit local-demo provisioning; never active in the default or test profiles. */
@Component
@Profile("demo-accounts")
public class DemoAccountsInitializer implements ApplicationRunner {
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private StaffProfileRepository staff;
    @Autowired private BranchRepository branches;
    @Autowired private PasswordEncoder encoder;
    @Autowired private EerService eer;
    @Autowired private ObjectMapper json;
    @Value("${ciao.demo.credentials-file}") private String credentialsFile;

    public record DemoAccount(String email, String name, String role, String code, String phone) {}
    public static final List<DemoAccount> ACCOUNTS = List.of(
        new DemoAccount("it25100691@ciao.test", "Warushawithana J.B. (Demo)", "OPERATIONS_MANAGER", "DEMO-IT25100691", "0709900001"),
        new DemoAccount("it25102586@ciao.test", "Jahaas M.J.M. (Demo)", "CUSTOMER_SERVICE_SUPERVISOR", "DEMO-IT25102586", "0709900002"),
        new DemoAccount("it25103474@ciao.test", "Dahanayake T.S. (Demo)", "E_TICKETING_COORDINATOR", "DEMO-IT25103474", "0709900003"),
        new DemoAccount("it25101627@ciao.test", "Govinna G.N.C. (Demo)", "FINANCE_MANAGER", "DEMO-IT25101627", "0709900004"),
        new DemoAccount("it25101753@ciao.test", "De Silva Y.Y.S. (Demo)", "OPERATIONS_MANAGER", "DEMO-IT25101753", "0709900005"),
        new DemoAccount("it25103647@ciao.test", "Sampath M.V. (Demo)", "BRANCH_MANAGER", "DEMO-IT25103647", "0709900006"),
        new DemoAccount("system-admin@ciao.test", "System Administrator (Demo)", "SYSTEM_ADMINISTRATOR", "DEMO-SYSADMIN", "0709900007"),
        new DemoAccount("passenger-demo@ciao.test", "Passenger (Demo)", "PASSENGER", "DEMO-PASSENGER", "0709900008")
    );

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        Map<String,String> passwords = json.readValue(Path.of(credentialsFile).toFile(),
                new com.fasterxml.jackson.core.type.TypeReference<Map<String,String>>() {});
        for (DemoAccount account : ACCOUNTS) {
            String password = passwords.get(account.email());
            if (password == null || password.length() < 12 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
                throw new IllegalStateException("A valid private password is required for " + account.email());
            var existing = users.findByEmailIgnoreCase(account.email());
            if (existing.isPresent()) {
                User user = existing.get();
                boolean passenger = account.role().equals("PASSENGER");
                boolean matches = encoder.matches(password, user.getPasswordHash()) && account.name().equals(user.getFullName())
                    && (passenger ? "PASSENGER".equals(user.getRole().getRoleName())
                    : staff.findByUserId(user.getId()).map(p -> account.code().equals(p.getEmployeeCode()) && account.role().equals(p.getStaffType())).orElse(false));
                if (!matches) throw new IllegalStateException("Existing account differs; refusing to overwrite " + account.email());
                continue;
            }
            User user = new User();
            user.setFullName(account.name()); user.setEmail(account.email()); user.setPhone(account.phone());
            user.setPasswordHash(encoder.encode(password));
            user.setRole(roles.findByRoleName(account.role().equals("PASSENGER") ? "PASSENGER" : "STAFF").orElseThrow());
            user = users.save(user);
            eer.profile(user);
            if (!account.role().equals("PASSENGER")) {
                StaffProfile profile = staff.findByUserId(user.getId()).orElseThrow();
                profile.setStaffType(account.role()); profile.setEmployeeCode(account.code()); profile.setHireDate(LocalDate.now());
                staff.save(profile);
                if (account.role().equals("BRANCH_MANAGER")) {
                    Branch branch = new Branch(); branch.setLocation("University Demo Branch");
                    branch.setContactNumber(account.phone()); branch.setManager(profile); branches.save(branch);
                }
            }
        }
    }
}
