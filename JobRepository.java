package org.example.jobscraperweb;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface JobRepository extends JpaRepository<Job, Long> {
    List<Job> findByTitleContainingIgnoreCase(String keyword);
    Page<Job> findByTitleContainingIgnoreCase(String keyword, Pageable pageable);
    boolean existsByUrl(String url);
    Page<Job> findBySourceIgnoreCase(String source, Pageable pageable);

    @Query("SELECT j FROM Job j WHERE " +
           "(LOWER(j.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(j.company) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND " +
           "LOWER(j.source) = LOWER(:source)")
    Page<Job> searchJobsBySource(@Param("keyword") String keyword, @Param("source") String source, Pageable pageable);

    @Query("SELECT j FROM Job j WHERE " +
           "LOWER(j.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(j.company) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<Job> searchJobs(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT COUNT(j) FROM Job j WHERE LOWER(j.source) = LOWER(:source)")
    long countBySource(@Param("source") String source);
}