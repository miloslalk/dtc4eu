package eu.dtc4eu.website.security;

import eu.dtc4eu.website.adminuser.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminUserDetailsService implements UserDetailsService {

    private final AdminUserRepository adminUserRepository;

    @Override
    @Transactional(readOnly = true)
    public AdminUserDetails loadUserByUsername(String username) {
        return adminUserRepository.findByUsername(username)
                .map(AdminUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user: " + username));
    }
}
