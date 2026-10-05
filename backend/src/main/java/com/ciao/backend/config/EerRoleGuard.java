package com.ciao.backend.config;
import com.ciao.backend.repository.StaffProfileRepository;
import com.ciao.backend.service.EerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.servlet.http.*;
import java.util.*;

@Configuration
public class EerRoleGuard implements WebMvcConfigurer {
    @Autowired EerService eer;
    @Autowired StaffProfileRepository staff;
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler)throws Exception{
                String path=request.getRequestURI();
                if(path.startsWith("/api/auth/") || path.startsWith("/api/eer/"))return true;
                var user=eer.currentUser();if(user==null || !user.getRole().getRoleName().replace("ROLE_","").equals("STAFF"))return true;
                String type=staff.findByUserId(user.getId()).map(p->p.getStaffType()).orElse("UNASSIGNED");
                if(type.equals("SYSTEM_ADMINISTRATOR"))return true;
                boolean allowed=path.startsWith("/api/routes")||path.startsWith("/api/schedules") ? request.getMethod().equals("GET") || type.equals("OPERATIONS_MANAGER")
                    :path.equals("/api/fleet/buses/active") && request.getMethod().equals("GET")?true
                    :path.startsWith("/api/fleet")?type.equals("OPERATIONS_MANAGER")
                    :path.startsWith("/api/parcels")?Set.of("BRANCH_MANAGER","OPERATIONS_MANAGER").contains(type)
                    :path.startsWith("/api/group-bookings")?Set.of("E_TICKETING_COORDINATOR","OPERATIONS_MANAGER","FINANCE_MANAGER").contains(type)
                    :path.startsWith("/api/lost-items")?type.equals("CUSTOMER_SERVICE_SUPERVISOR")
                    :request.getMethod().equals("GET");
                if(!allowed){response.setStatus(403);response.setContentType("application/json");response.getWriter().write("{\"message\":\"This action requires the appropriate staff role.\"}");}
                return allowed;
            }
        }).addPathPatterns("/api/**");
    }
}
