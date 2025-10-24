# Spring Data Solr - Clases Desactivadas

## ⚠️ Importante

Las siguientes clases han sido **temporalmente desactivadas** durante la migración a Spring Boot 3.x:

- `EntitySolr.java.disabled`
- `EntityIndexerSolrImpl.java.disabled`
- `EntitySolrRepository.java.disabled` (en `/repositories/solr/`)
- `EntitySearchServiceSolrImpl.java.disabled` (en `/search/solr/`)

## 📋 Razón

**Spring Data Solr fue deprecado y eliminado** en Spring Boot 3.x:
- Spring Data Solr fue marcado como deprecado en Spring Boot 2.5
- Completamente eliminado en Spring Boot 3.0
- Ya no existe el paquete `org.springframework.data.solr`

## 🔧 Opciones de Migración

### Opción 1: Usar Cliente Solr Directo (Recomendado)

Migrar a usar directamente el cliente de Apache Solr:

```java
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.common.SolrInputDocument;

// En lugar de @SolrDocument y SolrTemplate
SolrClient solrClient = new HttpSolrClient.Builder(solrUrl).build();
SolrInputDocument doc = new SolrInputDocument();
doc.addField("id", entityId);
doc.addField("type", entityType);
solrClient.add(doc);
solrClient.commit();
```

### Opción 2: Migrar a Elasticsearch

Considerar migrar a Elasticsearch que tiene mejor soporte en Spring Boot 3.x con Spring Data Elasticsearch.

### Opción 3: Usar SolrJ sin Spring Data

Implementar repositorios personalizados usando SolrJ directamente sin las abstracciones de Spring Data.

## 📚 Referencias

- [Spring Boot 3.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.0-Migration-Guide)
- [Apache Solr 9.x Documentation](https://solr.apache.org/guide/solr/latest/)
- [SolrJ Client API](https://solr.apache.org/guide/solr/latest/deployment-guide/solrj.html)

## 🔄 Para Reactivar

Si decides migrar estas clases manualmente:

1. Renombrar los archivos `.disabled` a `.java`
2. Reemplazar todas las anotaciones de Spring Data Solr:
   - `@SolrDocument` → eliminar o usar anotación personalizada
   - `@Indexed` → eliminar o usar anotación personalizada
   - `@Dynamic` → eliminar
3. Reemplazar `SolrTemplate` con `SolrClient`
4. Reemplazar `SolrCrudRepository` con implementación personalizada usando `SolrClient`
5. Actualizar queries de Solr para usar la API de SolrJ directamente

## 📅 Fecha de Desactivación

23 de octubre de 2025 - Durante migración a Spring Boot 3.5.0
