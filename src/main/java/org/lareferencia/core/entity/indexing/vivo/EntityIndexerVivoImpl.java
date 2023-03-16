package org.lareferencia.core.entity.indexing.vivo;

import java.io.IOException;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;

import org.apache.jena.rdf.model.ModelFactory;
import org.lareferencia.core.entity.indexing.service.IEntityIndexer;
import org.lareferencia.core.entity.indexing.vivo.config.EntityIndexingConfig;
import org.lareferencia.core.entity.indexing.vivo.config.IndexingConfiguration;
import org.lareferencia.core.entity.indexing.vivo.config.NamespaceConfig;
import org.lareferencia.core.entity.indexing.vivo.config.OutputConfig;

public class EntityIndexerVivoImpl extends AbstractEntityIndexerRDF implements IEntityIndexer {
	
	private String targetUrl;
	private String user;
	private String password;

	@Override
	public void flush() {

		// Transfer the processed data to VIVO and flush the RDF model
		try {
			transferData();
			flushModel();
		} catch (Exception e) {
			e.printStackTrace();
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
			
			try {
				OutputConfig endpoint = getOutputsByType(outputs, "post").get(0);
				targetUrl = endpoint.getUrl();
				user = endpoint.getUser();
				password = endpoint.getPassword();
				graph = endpoint.getGraph();
				
				// Use in-memory RDF model
				m = ModelFactory.createDefaultModel();
				persist = false;
			
			} catch (Exception e) {
				logger.error("No output of type 'post' defined in the config file.");
			}
			
		} catch (Exception e) {
			logger.error("RDF Mapping Config File: " + configFilePath + e.getMessage());
		}
	}	
	
	private void flushModel() {
		
		m.removeAll();
		m.close();
	}
	
	private String getModelContent () {
	
		StringWriter content = new StringWriter();
		m.write(content, "N3");

		return content.toString();
	}
	
	private long getModelSize () {
		
		return m.size();
	}
	
	private int sendUpdate (String update) {
		
		int result = 0;
		
		try {			
			//Set parameters
	        StringBuilder postData = new StringBuilder();
	        postData.append("email=" + URLEncoder.encode(user, "UTF-8"));
	        postData.append("&password=" + URLEncoder.encode(password, "UTF-8"));
	        postData.append("&update=" + URLEncoder.encode(update, "UTF-8"));
	        
	        byte[] postDataBytes = postData.toString().getBytes("UTF-8");
			
	        //Call SPARQL update endpoint
	        URL url = new URL(targetUrl);
	        HttpURLConnection con = (HttpURLConnection) url.openConnection();
			
			con.setRequestMethod("POST");
			con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded"); 
			con.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));
	        con.setDoOutput(true);
	        con.getOutputStream().write(postDataBytes);
			
	        result = con.getResponseCode();
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return result;
	} 
	
	private void transferData () throws Exception {
		
		String modelContent = getModelContent();
		String update = "INSERT DATA { GRAPH " + graph +  " { " + modelContent + " } }";
		int result = sendUpdate(update);
		
		if (result != 200) {
			throw new Exception ("Failed to transfer the data. Returned code " + result);
		}
		else {
			logger.info("Page successfully transferred to target destination. " + getModelSize() + " triples sent.");
		}
	}

}
