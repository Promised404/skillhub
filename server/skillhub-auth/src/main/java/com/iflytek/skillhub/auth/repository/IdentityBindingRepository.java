package com.iflytek.skillhub.auth.repository;

import com.iflytek.skillhub.auth.entity.IdentityBinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

/**
 * JPA repository for links between platform users and external identity-provider subjects.
 */
@Repository
public interface IdentityBindingRepository extends JpaRepository<IdentityBinding, Long> {
    Optional<IdentityBinding> findByProviderCodeAndSubject(String providerCode, String subject);
    Optional<IdentityBinding> findByProviderCodeAndLoginName(String providerCode, String loginName);
    java.util.List<IdentityBinding> findByUserId(String userId);
}
