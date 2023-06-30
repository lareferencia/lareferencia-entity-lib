package org.lareferencia.core.entity.indexing.vivo;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;

import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.tdb.TDBFactory;
import org.lareferencia.core.entity.indexing.service.IEntityIndexer;
import org.lareferencia.core.entity.indexing.vivo.config.EntityIndexingConfig;
import org.lareferencia.core.entity.indexing.vivo.config.IndexingConfiguration;
import org.lareferencia.core.entity.indexing.vivo.config.NamespaceConfig;
import org.lareferencia.core.entity.indexing.vivo.config.OutputConfig;

public class EntityIndexerTDBImpl extends AbstractEntityIndexerRDF implements IEntityIndexer {
	
	private static final int PAGE_SIZE = 1000000;
	
	@Override
	public void flush() {

		//Save RDF model to one or more files
		List<OutputConfig> files = getOutputsByType(outputs, "file");
		
		for (OutputConfig file : files) {
			String filePath = file.getPath() + file.getName();
			
			writeRDFModel(filePath, file.getFormat());
		}
	}

	@Override
	public void setConfig(String configFilePath) {
		
		try {
			indexingConfig = IndexingConfiguration.loadFromXml(configFilePath);
			outputs = indexingConfig.getOutputs();
			namespaces = new HashMap<String, String>();
			configsByEntityType = new HashMap<String, EntityIndexingConfig>();
			
			for (NamespaceConfig namespace : indexingConfig.getNamespaces()) {
				namespaces.put(namespace.getPrefix(), namespace.getUrl());
			}
			
			for (EntityIndexingConfig entityIndexingConfig: indexingConfig.getSourceEntities()){
				configsByEntityType.put(entityIndexingConfig.getType(), entityIndexingConfig);
			}
				
			logger.info("RDF Mapping Config File: " + configFilePath + " loaded.");
			
			// load filters for field occurrence filtering
			loadOccurFilters();
			
			try {
				OutputConfig tdb = getOutputsByType(outputs, "triplestore").get(0);
				graph = tdb.getGraph();				
			
				String directory = tdb.getPath();				
				boolean reset = Boolean.parseBoolean(tdb.getReset());
				
				// Use TDB triplestore
				dataset = TDBFactory.createDataset(directory);
				persist = true;
				
				//Clear the dataset if necessary
				if (reset) {
					clearDataset();
				}	
				
				logger.info("Using TDB triplestore at " + directory);
			
			} catch (Exception e) {
				e.printStackTrace();;
			}
			 	
		} catch (Exception e) {
			logger.error("RDF Mapping Config File: " + configFilePath + e.getMessage());
		}
	}
	
	private long getModelSize() {
		
		long modelSize = 0;
		
		dataset.begin(ReadWrite.READ);
		
		try {	
			setTDBModel();
			modelSize = m.size();
		}
		finally { 
			dataset.end(); 
		}	
		return modelSize;
	}
	
	private StmtIterator getModelPage() {
		
		Model submodel = ModelFactory.createDefaultModel();
		String query = "SELECT ?s ?p ?o WHERE {?s ?p ?o .} LIMIT " + PAGE_SIZE;
		
		dataset.begin(ReadWrite.READ);
		
		try {	
			setTDBModel();
		
			QueryExecution qe = QueryExecutionFactory.create(query, m);
			ResultSet resultSet = qe.execSelect();

			while (resultSet.hasNext()) {
				QuerySolution next = resultSet.next();
				Resource subject = next.getResource("?s");
				Property predicate = submodel.createProperty(next.get("?p").toString());
				RDFNode object = next.get("?o");

				submodel.add(subject, predicate, object);
			}
		}
		finally { 
			dataset.end(); 
		}
		
		return submodel.listStatements();
	}
	
	private void clearDataset() {
		
		logger.info("Reset option set to true. Clearing graph...");
		
		int offset = 0;
		int page = 1;
		long totalTriples = getModelSize();
		
		while (offset < totalTriples) {
			StmtIterator modelPage = getModelPage();
			
			dataset.begin(ReadWrite.WRITE);
			
			try {			
				setTDBModel();
				m.remove(modelPage);
				dataset.commit();
				
				offset = PAGE_SIZE * page++;		
			}	
			finally { 
				dataset.end(); 		
			}
			
			logger.info("Remaining triples: " + getModelSize());
		}
		
		logger.info("Graph cleared inside triplestore.");
	}
	
	private void writeRDFModel(String outputFilePath, String format) {
		
		dataset.begin(ReadWrite.READ);

		try {
			setTDBModel();
			
			// Write RDF file
			OutputStream writer = new FileOutputStream(outputFilePath);
			m.write(writer, format);
			writer.close();
		}
		catch (IOException e){
			e.printStackTrace();
		}
		finally { 
			dataset.end(); 
		}
	}

}
