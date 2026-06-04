package com.purplle.storeintelligence.repository;

import com.purplle.storeintelligence.entity.Store;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StoreRepository extends JpaRepository<Store, String> {

    boolean existsByStoreId(String storeId);
}
