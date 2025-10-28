package org.lareferencia.core.entity.services;

import java.util.Map;
import java.util.HashMap;

import org.hibernate.Hibernate;
import org.lareferencia.core.entity.domain.EntityType;
import org.lareferencia.core.entity.domain.FieldType;
import org.lareferencia.core.entity.domain.ICacheableNamedEntity;
import org.lareferencia.core.entity.domain.RelationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import jakarta.annotation.PreDestroy;

import org.lareferencia.core.entity.repositories.jpa.EntityTypeRepository;
import org.lareferencia.core.entity.repositories.jpa.FieldTypeRepository;
import org.lareferencia.core.entity.repositories.jpa.RelationTypeRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component
public class EntityModelCache {

    @Autowired
    RelationTypeRepository relationTypeRepository;

    @Autowired
    EntityTypeRepository entityTypeRepository;

    @Autowired
    FieldTypeRepository fieldTypeRepository;

    Map<String, Map<String, ? extends ICacheableNamedEntity<Long>>> byNameMapsByClass;
    Map<String, Map<Long, ? extends ICacheableNamedEntity<Long>>> byIdMapsByClass;
    Map<String, Map<Long,String>> namesByIdMapsByClass;

    Map<String, Map<String, Boolean>> isFromRelationMap;

    @Autowired
    private PlatformTransactionManager transactionManager;
    
    private volatile boolean initialized = false;
    private final Object initLock = new Object();

    /**
     * Lazy initialization of the cache.
     * This method is called automatically on first access.
     * It's thread-safe using double-checked locking pattern.
     */
    private void ensureInitialized() {
        if (!initialized) {
            synchronized (initLock) {
                if (!initialized) {
                    initialize();
                    initialized = true;
                }
            }
        }
    }

    /**
     * Initialize the cache by loading data from database.
     * This is called lazily on first access, not at application startup.
     */
    private void initialize() {
        // Initialize the maps
        byNameMapsByClass = new HashMap<String, Map<String, ? extends ICacheableNamedEntity<Long>>>();
        byIdMapsByClass = new HashMap<String, Map<Long, ? extends ICacheableNamedEntity<Long>>>();
        namesByIdMapsByClass = new HashMap<String, Map<Long, String>>();

        try {
            // Manually begin transaction
            TransactionStatus transactionStatus = transactionManager.getTransaction(new DefaultTransactionDefinition());

            try {
                // Populate the maps
                populateMaps(RelationType.class, relationTypeRepository);
                populateMaps(EntityType.class, entityTypeRepository);
                populateMaps(FieldType.class, fieldTypeRepository);

                populateIsFromRelationMap();

                // Commit transaction
                transactionManager.commit(transactionStatus);
            } catch (Exception e) {
                // Rollback transaction in case of error
                transactionManager.rollback(transactionStatus);
                throw e;
            }
        } catch (Exception e) {
            // Initialize empty maps to avoid NPE in application code
            initializeEmptyMaps();
            throw new RuntimeException("Failed to initialize EntityModelCache. Database tables may not exist yet.", e);
        }
    }

    private void initializeEmptyMaps() {
        // Initialize empty maps for each type to prevent NPE
        byNameMapsByClass.put(RelationType.class.getName(), new HashMap<>());
        byNameMapsByClass.put(EntityType.class.getName(), new HashMap<>());
        byNameMapsByClass.put(FieldType.class.getName(), new HashMap<>());
        
        byIdMapsByClass.put(RelationType.class.getName(), new HashMap<>());
        byIdMapsByClass.put(EntityType.class.getName(), new HashMap<>());
        byIdMapsByClass.put(FieldType.class.getName(), new HashMap<>());
        
        namesByIdMapsByClass.put(RelationType.class.getName(), new HashMap<>());
        namesByIdMapsByClass.put(EntityType.class.getName(), new HashMap<>());
        namesByIdMapsByClass.put(FieldType.class.getName(), new HashMap<>());
        
        isFromRelationMap = new HashMap<>();
    }

    @PreDestroy
    public void preDestroy() {
        // Only clean up if the cache was initialized
        if (!initialized) {
            return;
        }
        
        // Clean up
        for (Map<String, ? extends ICacheableNamedEntity<Long>> map : byNameMapsByClass.values()) {
            map.clear();
        }

        for (Map<Long, ? extends ICacheableNamedEntity<Long>> map : byIdMapsByClass.values()) {
            map.clear();
        }

        byNameMapsByClass.clear();
        byIdMapsByClass.clear();
    }

    private void populateIsFromRelationMap() {
        isFromRelationMap = new HashMap<String, Map<String, Boolean>>();

        // for every relation type, get the RelationName, the fromEntityType and the toEntityType
        // use Relation name as key of the isFromRelationMap, then create another map, 
        // obtain the fromEntityType name and the toEntityType name,
        // and create a key with the fromEntityType name and the toEntityType name
        // the value will be true if the key is fromEntityType name and false if the key is toEntityType name
        for (RelationType relationType : relationTypeRepository.findAll()) {
            String relationName = relationType.getName();
            String fromEntityTypeName = relationType.getFromEntityType().getName();
            String toEntityTypeName = relationType.getToEntityType().getName();

            Map<String, Boolean> fromToMap = isFromRelationMap.get(relationName);
            if (fromToMap == null) {
                fromToMap = new HashMap<String, Boolean>();
                isFromRelationMap.put(relationName, fromToMap);
            }

            fromToMap.put(fromEntityTypeName, true);
            fromToMap.put(toEntityTypeName, false);
        }
    }

    public boolean isFromRelation(String relationName, String entityTypeName) {
        ensureInitialized();
        
        if (!isFromRelationMap.containsKey(relationName)) {
            throw new IllegalArgumentException("Relation name not found: " + relationName);
        }

        if (!isFromRelationMap.get(relationName).containsKey(entityTypeName)) {
            throw new IllegalArgumentException("Entity type name not found: " + entityTypeName + " in relation: " + relationName);
        }

        return isFromRelationMap.get(relationName).get(entityTypeName);
    }

    @SuppressWarnings("unchecked")
    public <T extends ICacheableNamedEntity<Long>>  Map<String, T> getByNameMap(Class<?> clazz) {
        ensureInitialized();
        return (Map<String, T>) byNameMapsByClass.get(clazz.getSimpleName());
    }

    @SuppressWarnings("unchecked")
    public <T extends ICacheableNamedEntity<Long>>  Map<Long, T> getByIdMap(Class<?> clazz) {
        ensureInitialized();
        return (Map<Long, T>) byIdMapsByClass.get(clazz.getSimpleName());
	}

    public <T extends ICacheableNamedEntity<Long>>  Map<Long, String> getNamesByIdMap(Class<?> clazz) {
        ensureInitialized();
        return (Map<Long, String>) namesByIdMapsByClass.get(clazz.getSimpleName());
    }
   
    @SuppressWarnings("unchecked")
    public <T extends ICacheableNamedEntity<Long>>  T getObjectByName(Class<?> clazz, String name) throws CacheException {
        ensureInitialized();
        T t = (T) byNameMapsByClass.get(clazz.getSimpleName()).get(name);
        if (t == null) {
            throw new CacheException("Not found in database: " + clazz.getSimpleName() + " " + name);
        }
        return t;
    }

    @SuppressWarnings("unchecked")
    public <T extends ICacheableNamedEntity<Long>>  T getObjectById(Class<?> clazz, Long id) throws CacheException {
        ensureInitialized();
        T t = (T) byIdMapsByClass.get(clazz.getSimpleName()).get(id);
        if (t == null) {
            throw new CacheException("Not found in database: " + clazz.getSimpleName() + " " + id);
        }
        return (T) byIdMapsByClass.get(clazz.getSimpleName()).get(id);
    }

    // Get the name of the object by its id
    public <T extends ICacheableNamedEntity<Long>> String getNameById(Class<?> clazz, Long id) throws CacheException {
        ensureInitialized();
        return getObjectById(clazz, id).getName();
    }

    // Get the id of the object by its name
    public <T extends ICacheableNamedEntity<Long>> Long getIdByName(Class<?> clazz, String name) throws CacheException {
        ensureInitialized();
        return getObjectByName(clazz, name).getId();
    }

   
    
    /**
     * This method populates the maps with the data from the repository, creating 
     * a map with the name as key and the id as value and another map with the id as key and the name as value
     * 
     * @param <T> Type Class
     * @param <R> Repository Class
     * @param byName map
     * @param byId map
     * @param repository
     */
    private <T extends ICacheableNamedEntity<Long>, R extends JpaRepository<T, Long>> 
        void populateMaps(Class<?> clazz, R repository) {

            Map<String,T> byName = new HashMap<String,T>();
            Map<Long,T> byId = new HashMap<Long,T>();
            Map<Long,String> namesById = new HashMap<Long,String>();

            String className = clazz.getSimpleName();

            byNameMapsByClass.put(className, byName);
            byIdMapsByClass.put(className, byId);
            namesByIdMapsByClass.put(className, namesById);

            for (T t : repository.findAll()) {
                Hibernate.initialize(t);
                byName.put(t.getName(), t);
                byId.put(t.getId(), t);
                namesById.put(t.getId(), t.getName());
            }
    }
        
}
