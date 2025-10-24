/*
 * Ejemplo de configuración para diferentes escenarios de uso
 * JSONElasticEntityIndexerThreadedImpl - Configuraciones Optimizadas
 */

package org.lareferencia.core.entity.indexing.elastic.config;

import org.lareferencia.core.entity.indexing.elastic.JSONElasticEntityIndexerThreadedImpl;
import org.lareferencia.core.entity.indexing.service.IEntityIndexer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configuraciones optimizadas para diferentes escenarios de uso del indexador Elasticsearch
 */
@Configuration
public class ElasticIndexerOptimizedConfig {

    /**
     * Configuración para desarrollo - Recursos limitados, debugging habilitado
     */
    @Bean
    @ConditionalOnProperty(name = "elastic.indexer.profile", havingValue = "development")
    public IEntityIndexer developmentElasticIndexer() {
        JSONElasticEntityIndexerThreadedImpl indexer = new JSONElasticEntityIndexerThreadedImpl();
        // Configuración conservadora para desarrollo
        // elastic.indexer.threads=2
        // elastic.indexer.writer.threads=2
        // elastic.indexer.buffer.size=5000
        return indexer;
    }

    /**
     * Configuración para testing - Balanceado entre performance y estabilidad
     */
    @Bean
    @ConditionalOnProperty(name = "elastic.indexer.profile", havingValue = "testing")
    public IEntityIndexer testingElasticIndexer() {
        JSONElasticEntityIndexerThreadedImpl indexer = new JSONElasticEntityIndexerThreadedImpl();
        // Configuración moderada para testing
        // elastic.indexer.threads=4
        // elastic.indexer.writer.threads=3
        // elastic.indexer.buffer.size=8000
        return indexer;
    }

    /**
     * Configuración para producción con cluster pequeño
     */
    @Bean
    @ConditionalOnProperty(name = "elastic.indexer.profile", havingValue = "production-small")
    public IEntityIndexer productionSmallElasticIndexer() {
        JSONElasticEntityIndexerThreadedImpl indexer = new JSONElasticEntityIndexerThreadedImpl();
        // Configuración optimizada para cluster pequeño
        // elastic.indexer.threads=4
        // elastic.indexer.writer.threads=3
        // elastic.indexer.buffer.size=10000
        // elastic.indexer.max.concurrent.tasks=8
        return indexer;
    }

    /**
     * Configuración para producción con cluster grande - Máxima performance
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "elastic.indexer.profile", havingValue = "production-large")
    public IEntityIndexer productionLargeElasticIndexer() {
        JSONElasticEntityIndexerThreadedImpl indexer = new JSONElasticEntityIndexerThreadedImpl();
        // Configuración de alta performance para cluster grande
        // elastic.indexer.threads=8
        // elastic.indexer.writer.threads=6
        // elastic.indexer.buffer.size=20000
        // elastic.indexer.max.concurrent.tasks=16
        return indexer;
    }

    /**
     * Configuración para procesamiento batch - Optimizado para grandes volúmenes
     */
    @Bean
    @ConditionalOnProperty(name = "elastic.indexer.profile", havingValue = "batch")
    public IEntityIndexer batchElasticIndexer() {
        JSONElasticEntityIndexerThreadedImpl indexer = new JSONElasticEntityIndexerThreadedImpl();
        // Configuración para procesamiento masivo
        // elastic.indexer.threads=12
        // elastic.indexer.writer.threads=8
        // elastic.indexer.buffer.size=50000
        // elastic.indexer.max.concurrent.tasks=24
        return indexer;
    }

    /**
     * Configuración conservadora para recursos limitados
     */
    @Bean
    @ConditionalOnProperty(name = "elastic.indexer.profile", havingValue = "limited")
    public IEntityIndexer limitedResourcesElasticIndexer() {
        JSONElasticEntityIndexerThreadedImpl indexer = new JSONElasticEntityIndexerThreadedImpl();
        // Configuración para recursos limitados
        // elastic.indexer.threads=2
        // elastic.indexer.writer.threads=1
        // elastic.indexer.buffer.size=3000
        // elastic.indexer.max.concurrent.tasks=4
        return indexer;
    }
}

/*
 * Archivo de propiedades de ejemplo: application-elastic-profiles.properties
 *
 * # Perfil de desarrollo
 * spring.profiles.active=development
 * elastic.indexer.profile=development
 * elastic.indexer.threads=2
 * elastic.indexer.writer.threads=2
 * elastic.indexer.buffer.size=5000
 * elastic.indexer.max.concurrent.tasks=4
 * elastic.indexer.monitoring.interval=5
 * 
 * # Perfil de producción cluster grande
 * spring.profiles.active=production
 * elastic.indexer.profile=production-large
 * elastic.indexer.threads=8
 * elastic.indexer.writer.threads=6
 * elastic.indexer.buffer.size=20000
 * elastic.indexer.max.concurrent.tasks=16
 * elastic.indexer.monitoring.interval=10
 * 
 * # Perfil de procesamiento batch
 * spring.profiles.active=batch
 * elastic.indexer.profile=batch
 * elastic.indexer.threads=12
 * elastic.indexer.writer.threads=8
 * elastic.indexer.buffer.size=50000
 * elastic.indexer.max.concurrent.tasks=24
 * elastic.indexer.monitoring.interval=30
 * 
 * # Configuración común de Elasticsearch
 * elastic.host=elasticsearch-cluster.example.com
 * elastic.port=9200
 * elastic.username=${ELASTIC_USERNAME:admin}
 * elastic.password=${ELASTIC_PASSWORD:secret}
 * elastic.useSSL=true
 * elastic.authenticate=true
 */
