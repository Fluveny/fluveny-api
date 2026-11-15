package com.fluveny.fluveny_backend.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.fluveny.fluveny_backend.platform.security.Role;
import com.fluveny.fluveny_backend.platform.security.RoleHierarchyConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;

/** Hierarquia única ADMIN > CONTENT_CREATOR, com STUDENT de fora (AD-12). */
class RoleHierarchyTest {

    private final RoleHierarchy hierarchy = RoleHierarchyConfig.roleHierarchy();

    @Test
    void adminReachesContentCreator() {
        assertThat(reachable(Role.ADMIN))
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_CONTENT_CREATOR");
    }

    @Test
    void contentCreatorReachesOnlyItself() {
        assertThat(reachable(Role.CONTENT_CREATOR)).containsExactly("ROLE_CONTENT_CREATOR");
    }

    @Test
    void studentIsOutsideTheHierarchy() {
        assertThat(reachable(Role.STUDENT)).containsExactly("ROLE_STUDENT");
    }

    private List<String> reachable(Role role) {
        return this.hierarchy
                .getReachableGrantedAuthorities(
                        AuthorityUtils.createAuthorityList(role.authority()))
                .stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }
}
