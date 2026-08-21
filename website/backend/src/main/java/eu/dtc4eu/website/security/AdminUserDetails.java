package eu.dtc4eu.website.security;

import eu.dtc4eu.website.adminuser.entity.AdminUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/** Authenticated CMS principal. */
public class AdminUserDetails implements UserDetails {

    private final AdminUser user;

    public AdminUserDetails(AdminUser user) {
        this.user = user;
    }

    public AdminUser getUser() {
        return user;
    }

    public Long getId() {
        return user.getId();
    }

    public AdminUser.Role getRole() {
        return user.getRole();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isEnabled() {
        return user.isActive();
    }
}
