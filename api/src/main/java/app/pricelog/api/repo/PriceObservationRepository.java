package app.pricelog.api.repo;

import app.pricelog.api.domain.Chain;
import app.pricelog.api.domain.PriceObservation;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PriceObservationRepository extends JpaRepository<PriceObservation, Long> {

    Optional<PriceObservation> findByIdAndUserId(Long id, Long userId);

    long countByStoreIdAndUserId(Long storeId, Long userId);

    /** Newest first, so the first row is the most recent sighting. */
    List<PriceObservation> findByUserIdAndProductIdOrderByObservedOnDescIdDesc(Long userId, Long productId);

    @Query("select min(o.priceCents) from PriceObservation o where o.userId = :userId and o.product.id = :productId")
    Integer findLowestPriceCents(@Param("userId") Long userId, @Param("productId") Long productId);

    @Query("""
            select distinct o.itemNumber from PriceObservation o
            where o.userId = :userId and o.itemNumber is not null and o.store.chain = :chain
            """)
    List<String> findItemNumbersForChain(@Param("userId") Long userId,
                                         @Param("chain") Chain chain);

    @Query("""
            select o from PriceObservation o
            join fetch o.product p
            join fetch o.store s
            join app.pricelog.api.domain.UserProductWatch w on w.productId = p.id
            where o.userId = :userId and w.userId = :userId and w.watched = true
            order by o.observedOn desc, o.id desc
            """)
    List<PriceObservation> findWatched(@Param("userId") Long userId);

    @Query("""
            select o from PriceObservation o
            join fetch o.product p
            join fetch o.store s
            where o.userId = :userId and p.id in :productIds
            order by o.observedOn desc, o.id desc
            """)
    List<PriceObservation> findForProducts(@Param("userId") Long userId,
                                           @Param("productIds") Collection<Long> productIds);

    @Query("""
            select o from PriceObservation o
            join fetch o.product p
            join fetch o.store s
            where o.userId = :userId and o.needsReview = true
            order by o.createdAt desc
            """)
    List<PriceObservation> findPendingReview(@Param("userId") Long userId);

    @Query("""
            select o from PriceObservation o
            join fetch o.product p
            join fetch o.store s
            where o.userId = :userId
            order by o.observedOn desc, o.id desc
            """)
    List<PriceObservation> findRecent(@Param("userId") Long userId, Pageable pageable);

    @Query("""
            select o from PriceObservation o
            join fetch o.product p
            join fetch o.store s
            where o.userId = :userId
            order by o.observedOn desc, o.id desc
            """)
    List<PriceObservation> findAllNewestFirst(@Param("userId") Long userId);

    List<PriceObservation> findByUserIdOrderByObservedOnDescIdDesc(Long userId);

    @Query("select o.photoUrl from PriceObservation o where o.userId = :userId and o.photoUrl is not null")
    List<String> findPhotoUrlsByUserId(@Param("userId") Long userId);

    @Query(value = """
            select o from PriceObservation o
            join fetch o.product p
            join fetch o.store s
            where o.userId = :userId
            """,
            countQuery = "select count(o) from PriceObservation o where o.userId = :userId")
    Page<PriceObservation> pageByUserId(@Param("userId") Long userId, Pageable pageable);
}
