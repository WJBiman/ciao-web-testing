package com.ciao.backend.security;

import com.ciao.backend.entity.User;
import com.ciao.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {
    
    @Autowired
    UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String identifier = AccountIdentifiers.normalize(username);
        User user;
        if (identifier.contains("@")) {
            user = userRepository.findByEmailIgnoreCase(identifier)
                .orElseThrow(() -> new UsernameNotFoundException("Account not found"));
        } else if (userRepository.findByUsernameIgnoreCase(identifier).isPresent()) {
            user = userRepository.findByUsernameIgnoreCase(identifier).orElseThrow();
        } else {
            var matches = userRepository.findByPhoneIn(AccountIdentifiers.phoneForms(identifier));
            // Never select an arbitrary account if legacy data has duplicate mobile formats.
            if (matches.size() != 1) throw new UsernameNotFoundException("Use your email to sign in");
            user = matches.get(0);
        }

        return UserDetailsImpl.build(user);
    }
}
