package org.lareferencia.core.entity.indexing.vivo;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;

import org.apache.jena.query.ReadWrite;
import org.apache.jena.tdb.TDBFactory;
import org.lareferencia.core.entity.indexing.service.IEntityIndexer;
import org.lareferencia.core.entity.indexing.vivo.config.EntityIndexingConfig;
import org.lareferencia.core.entity.indexing.vivo.config.IndexingConfiguration;
import org.lareferencia.core.entity.indexing.vivo.config.NamespaceConfig;
import org.lareferencia.core.entity.indexing.vivo.config.OutputConfig;

public class EntityIndexerTDBImpl extends AbstractEntityIndexerRDF implements IEntityIndexer {
	
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
				logger.error("No output of type 'triplestore' defined in the config file.");
			}
			 	
		} catch (Exception e) {
			logger.error("RDF Mapping Config File: " + configFilePath + e.getMessage());
		}
	}	
	
	private void clearDataset() {
		
		dataset.begin(ReadWrite.WRITE);
		
		try {	
			logger.info("Reset option set to true. Clearing graph...");
			
			setTDBModel();
			m.removeAll();
			dataset.commit();
			
			logger.info("Graph cleared inside triplestore.");
		}
		finally { 
			dataset.end(); 
		}	
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
