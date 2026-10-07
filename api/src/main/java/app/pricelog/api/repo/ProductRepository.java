package app.pricelog.api.repo;

import app.pricelog.api.domain.Category;
import app.pricelog.api.domain.Product;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByNormalizedKey(String normalizedKey);

    List<Product> findByComparisonKey(String comparisonKey);

    List<Product> findByCategoryOrderByDisplayNameAsc(Category category);

    @Query("""
            select p from Product p
            where lower(p.displayName) like lower(concat('%', :q, '%'))
               or lower(coalesce(p.brand, '')) like lower(concat('%', :q, '%'))
            order by p.displayName asc
            """)
    List<Product> search(@Param("q") String q);

    /**
     * The catalog is shared, but a user only ever sees the products they have
     * logged: every product read joins through their own observations.
     */
    @Query("""
            select distinct p from Product p
            join app.pricelog.api.domain.PriceObservation o on o.product.id = p.id
            where o.userId = :userId
              and (lower(p.displayName) like lower(concat('%', :q, '%'))
                or lower(coalesce(p.brand, '')) like lower(concat('%', :q, '%')))
            order by p.displayName asc
            """)
    List<Product> searchForUser(@Param("q") String q, @Param("userId") Long userId);

    @Query("""
            select distinct p from Product p
            join app.pricelog.api.domain.PriceObservation o on o.product.id = p.id
            where o.userId = :userId and p.category = :category
            order by p.displayName asc
            """)
    List<Product> findByCategoryForUser(@Param("category") Category category,
                                        @Param("userId") Long userId);

    @Query("""
            select distinct p from Product p
            join app.pricelog.api.domain.PriceObservation o on o.product.id = p.id
            where o.userId = :userId and p.comparisonKey = :comparisonKey
            """)
    List<Product> findByComparisonKeyForUser(@Param("comparisonKey") String comparisonKey,
                                             @Param("userId") Long userId);

    @Query("""
            select distinct p.id from Product p
            join app.pricelog.api.domain.PriceObservation o on o.product.id = p.id
            where o.userId = :userId and p.id in :productIds
            """)
    List<Long> filterToObservedIds(@Param("userId") Long userId,
                                   @Param("productIds") Collection<Long> productIds);
}
