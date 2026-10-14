package se327.bookstore.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import se327.bookstore.entity.Role;
import se327.bookstore.entity.User;

import java.util.Collection;
import java.util.List;

@Getter
public class BookstoreUserDetails implements UserDetails {

    private final Long id;
    private final String email;
    private final String displayName;
    private final String password;
    private final Role role;
    private final Collection<? extends GrantedAuthority> authorities;

    public BookstoreUserDetails(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.displayName = user.getUsername();
        this.password = user.getPasswordHash();
        this.role = user.getRole();
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
