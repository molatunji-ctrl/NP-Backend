package org.admin.npapplication.identity.persistence;

import org.admin.npapplication.identity.domain.AccountToken;
import org.admin.npapplication.identity.domain.AccountTokenType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface AccountTokenRepository extends JpaRepository<AccountToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select token from AccountToken token
            join fetch token.user
            where token.tokenHash = :tokenHash and token.type = :type
            """)
    Optional<AccountToken> findForUpdate(
            @Param("tokenHash") String tokenHash,
            @Param("type") AccountTokenType type
    );

    void deleteByUserIdAndType(Long userId, AccountTokenType type);

    void deleteByUserId(Long userId);
}
