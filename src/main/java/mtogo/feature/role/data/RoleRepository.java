package mtogo.feature.role.data;

import java.util.Optional;
import mtogo.feature.role.domain.Role;
import mtogo.feature.role.domain.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long>
{
    Optional<Role> findByName(RoleName name);
}
