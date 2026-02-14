package com.credentials.repo;

import com.credentials.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findBySubjectId(String subjectId);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END " +
            "FROM User u " +
            "JOIN u.organizations o " +
            "WHERE u.subjectId = :subjectId AND o.id = :orgId")
    boolean isUserMemberOfOrg(@Param("subjectId") String subjectId, @Param("orgId") UUID orgId);
}