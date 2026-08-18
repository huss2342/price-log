package app.pricelog.api.repo;

import app.pricelog.api.domain.Category;
import app.pricelog.api.domain.Product;
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
}
