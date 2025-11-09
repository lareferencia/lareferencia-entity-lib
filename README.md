# LA Referencia Entity Library

Entity management, relationship processing, and multi-engine indexing library for scholarly metadata.

## 🎯 Functionality

### Entity Domain (`org.lareferencia.core.entity.domain`)
Entity models representing scholarly objects (Publications, Persons, Organizations, Projects) with bidirectional relationships, provenance tracking, and semantic identifier management (DOI, ORCID, ROR).

### Entity Services (`org.lareferencia.core.entity.services`)
Business logic for entity CRUD operations, relationship management, caching, and statistics tracking.

### Entity Indexing (`org.lareferencia.core.entity.indexing`)
Multi-engine indexing system supporting:
- **Elasticsearch**: Multi-threaded indexer with circuit breaker and backpressure control (v5.0)
- **Triple Stores**: VIVO-compatible RDF indexing (Apache Jena TDB1/TDB2)

### Entity Repositories (`org.lareferencia.core.entity.repositories.jpa`)
JPA repositories for entity persistence and data access.

## � License

Licensed under the **GNU Affero General Public License v3.0 (AGPL-3.0)**.  
See [LICENSE.txt](../LICENSE.txt) for complete terms.

## � Support

**Email**: soporte@lareferencia.redclara.net

---

**LA Referencia** - Red Latinoamericana y de España de Ciencia Abierta  
Part of the LA Referencia Platform v4.2.6 / v5.0
