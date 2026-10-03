# LA Referencia Entity Library

Entity management, relationship processing, and multi-engine indexing library for scholarly metadata.

## 🎯 Functionality

### Entity Domain (`org.lareferencia.core.entity.domain`)
Entity models representing scholarly objects (Publications, Persons, Organizations, Projects) with bidirectional relationships, provenance tracking, semantic identifier management (DOI, ORCID, ROR) and a `deleted` flag used to exclude logically deleted entities from indexing (see [`docs/ENTITY_DELETED_INDEXING.md`](../docs/ENTITY_DELETED_INDEXING.md)).

### Entity Services (`org.lareferencia.core.entity.services`)
Business logic for entity CRUD operations, relationship management, dirty-entity merging (`mergeDirtyEntitiesAndRelations`), caching, and statistics tracking.

### Entity Indexing (`org.lareferencia.core.entity.indexing`)
Multi-engine indexing system supporting:
- **Elasticsearch/OpenSearch**: fixed thread pool (`availableProcessors`) with semaphore backpressure, per-document read-only `REQUIRES_NEW` transactions and a circuit breaker — see [`docs/ENTITY_INDEXING_ARCHITECTURE.md`](../docs/ENTITY_INDEXING_ARCHITECTURE.md)
- **Triple Stores**: VIVO-compatible RDF indexing (Apache Jena TDB1/TDB2)

### Entity Repositories (`org.lareferencia.core.entity.repositories.jpa`)
JPA repositories for entity persistence and data access (pagination filters by `dirty=false` and `deleted=false`).

## 📄 License

Licensed under the **GNU Affero General Public License v3.0 (AGPL-3.0)**.  
See [LICENSE.txt](../LICENSE.txt) for complete terms.

## 📧 Support

**Email**: soporte@lareferencia.redclara.net

---

**LA Referencia** - Red Latinoamericana y de España de Ciencia Abierta  
Part of the LA Referencia Platform 5.0.0-rc3
