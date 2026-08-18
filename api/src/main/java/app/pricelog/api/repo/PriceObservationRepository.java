package app.pricelog.api.repo;

import app.pricelog.api.domain.PriceObservation;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PriceObservationRepository extends JpaRepository<PriceObservation, Long> {

    @Query("""
            select o from PriceObservation o
            join fetch o.product p
            join fetch o.store s
            where p.id in :productIds
            order by o.observedOn desc, o.id desc
            """)
    List<PriceObservation> findForProducts(@Param("productIds") Collection<Long> productIds);

    @Query("""
            select o from PriceObservation o
            join fetch o.product p
            join fetch o.store s
            where o.needsReview = true
            order by o.createdAt desc
            """)
    List<PriceObservation> findPendingReview();

    @Query("""
            select o from PriceObservation o
            join fetch o.product p
            join fetch o.store s
            order by o.observedOn desc, o.id desc
            """)
    List<PriceObservation> findRecent(org.springframework.data.domain.Pageable pageable);
}
