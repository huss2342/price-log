package app.pricelog.api.security;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    /** The owner: the first account ever created, which V7 seeded. */
    Optional<AppUser> findFirstByOrderByIdAsc();
}
