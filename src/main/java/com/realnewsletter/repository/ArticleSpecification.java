package com.realnewsletter.repository;

import com.realnewsletter.model.Article;
import com.realnewsletter.model.NewsdataArticle;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * JPA Specifications for filtering {@link Article} entities.
 *
 * <p>{@code country}, {@code language}, and {@code category} are stored as
 * comma-separated strings (e.g. {@code "in,us"}) on {@link NewsdataArticle}.
 * Matching is done by padding both the column and the search value with commas:
 * <pre>
 *   CONCAT(',', column, ',') LIKE CONCAT('%,', :value, ',%')
 * </pre>
 * This ensures {@code country=in} matches {@code "in,us"} and {@code "us,in"}
 * but NOT {@code "china"} or {@code "argentina"}.
 */
public class ArticleSpecification {

    private ArticleSpecification() {}

    /**
     * Returns a {@link Specification} that AND-combines whichever of
     * {@code country}, {@code language}, {@code category} are non-blank.
     * Passing all nulls/blanks returns every article (no WHERE clause added).
     */
    public static Specification<Article> withFilters(String country,
                                                     String language,
                                                     String category) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // These fields live on NewsdataArticle; treat() accesses them via
            // the same table (SINGLE_TABLE) and adds the discriminator condition.
            Root<NewsdataArticle> nd = cb.treat(root, NewsdataArticle.class);

            if (hasValue(country)) {
                predicates.add(tokenLike(cb, nd.get("country"), country));
            }
            if (hasValue(language)) {
                predicates.add(tokenLike(cb, nd.get("language"), language));
            }
            if (hasValue(category)) {
                predicates.add(tokenLike(cb, nd.get("category"), category));
            }

            return predicates.isEmpty()
                    ? cb.conjunction()               // no filter → match all
                    : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static boolean hasValue(String s) {
        return s != null && !s.isBlank();
    }

    /**
     * Builds a case-insensitive "whole-token" LIKE predicate by wrapping both
     * the column and the search value in commas before comparing.
     *
     * <pre>SQL: LOWER(CONCAT(',', col, ',')) LIKE LOWER('%,value,%')</pre>
     */
    private static Predicate tokenLike(CriteriaBuilder cb,
                                       Expression<String> column,
                                       String value) {
        // Wrap column: "," || col || ","
        Expression<String> padded = cb.concat(
                cb.concat(cb.literal(","), column),
                cb.literal(","));

        String pattern = ("%," + value.trim().toLowerCase() + ",%");
        return cb.like(cb.lower(padded), pattern);
    }
}

