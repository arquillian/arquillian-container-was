package org.jboss.arquillian.container.was.wlp_remote_8_5;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 
 * This is a wrapper around the Websphere Liberty JMX Rest API.
 * 
 * @author <a href="mailto:tayres@gmail.com">Tony Ayres</a>
 *
 */
public class WLPRestClient {

    private static final String className = WLPRestClient.class.getName();

    private static Logger log = Logger.getLogger(className);

    private WLPRemoteContainerConfiguration configuration;

    private static final String IBMJMX_CONNECTOR_REST = "/IBMJMXConnectorREST";
    private static final String FILE_ENDPOINT = IBMJMX_CONNECTOR_REST + "/file/";
    private static final String MBEANS_ENDPOINT = IBMJMX_CONNECTOR_REST + "/mbeans/";
    private static final String UTF_8 = "UTF-8";
    private static final String STARTED = "STARTED";

    private final Executor executor;

    public WLPRestClient(WLPRemoteContainerConfiguration configuration) {
        this.configuration = configuration;
        executor = Executor.newInstance().auth(new HttpHost(configuration.getHostName()), configuration.getUsername(),
                configuration.getPassword());
    }

    /**
     * Uses the rest api to upload an application binary to the dropins folder
     * of WLP to allow the server automatically deploy it.
     * 
     * @param archive
     * @throws ClientProtocolException
     * @throws IOException
     */
    public void deploy(File archive) throws ClientProtocolException, IOException {

        if (log.isLoggable(Level.FINER)) {
            log.entering(className, "deploy");
        }

        String deployPath = String.format("${wlp.user.dir}/servers/%s/dropins/%s", configuration.getServerName(),
                archive.getName());

        String serverRestEndpoint = String.format("https://%s:%d%s%s", configuration.getHostName(),
                configuration.getHttpsPort(), FILE_ENDPOINT, URLEncoder.encode(deployPath, UTF_8));

        HttpResponse result = executor.execute(
                Request.Post(serverRestEndpoint).useExpectContinue().version(HttpVersion.HTTP_1_1)
                        .bodyFile(archive, ContentType.DEFAULT_BINARY)).returnResponse();

        if (log.isLoggable(Level.FINE)) {
            log.fine("While deploying file " + archive.getName() + ", server returned response code "
                    + result.getStatusLine().getStatusCode());
        }

        if (!(result.getStatusLine().getStatusCode() >= HttpStatus.SC_OK && result.getStatusLine().getStatusCode() <= HttpStatus.SC_NO_CONTENT)) {
            throw new ClientProtocolException("Could not deploy application to server, server returned response: "
                    + result.getStatusLine());
        }

        if (log.isLoggable(Level.FINER)) {
            log.exiting(className, "deploy");
        }

    }

    /**
     * Deletes the specified application from the servers dropins directory. WLP
     * will detect this and then undeploy it.
     * 
     * @param String
     *            - applicationName
     * @throws ClientProtocolException
     * @throws IOException
     */
    public void undeploy(String applicationName) throws ClientProtocolException, IOException {

        if (log.isLoggable(Level.FINER)) {
            log.entering(className, "undeploy");
        }

        String deployPath = String.format("${wlp.user.dir}/servers/%s/dropins/%s", configuration.getServerName(),
                applicationName);

        String serverRestEndpoint = String.format("https://%s:%d%s%s", configuration.getHostName(),
                configuration.getHttpsPort(), FILE_ENDPOINT, URLEncoder.encode(deployPath, UTF_8));

        int result = executor.execute(Request.Delete(serverRestEndpoint).useExpectContinue().version(HttpVersion.HTTP_1_1))
                .returnResponse().getStatusLine().getStatusCode();

        if (result == HttpStatus.SC_NO_CONTENT) {
            log.fine("File " + applicationName + " was deleted");
            // wait to allow the server to detect the app has been deleted
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                log.severe("Thread sleep error " + e);
            }
        }

        if (log.isLoggable(Level.FINER)) {
            log.exiting(className, "isServerUp", result);
        }
    }

    /**
     * Calls the rest api to determine if the application server is up and
     * running.
     * 
     * @return boolean - true if the server is running
     */
    public boolean isServerUp() throws ClientProtocolException, IOException {
        if (log.isLoggable(Level.FINER)) {
            log.entering(className, "isServerUp");
        }

        String hostName = String.format("https://%s:%d%s", configuration.getHostName(), configuration.getHttpsPort(),
                IBMJMX_CONNECTOR_REST);

        int result = executor.execute(Request.Get(hostName)).returnResponse().getStatusLine().getStatusCode();

        if (log.isLoggable(Level.FINER)) {
            log.exiting(className, "isServerUp");
        }

        if (result == 200) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Checks the application State using the WLP rest api.
     * 
     * @param applicationName
     * @return true if the application is in STARTED state
     * @throws DeploymentException
     */
    public boolean isApplicationStarted(String applicationName) {

        if (log.isLoggable(Level.FINER)) {
            log.entering(className, "isApplicationStarted");
        }

        String restEndpoint = String.format(
                "https://%s:%d%sWebSphere:service=com.ibm.websphere.application.ApplicationMBean,name=%s/attributes/State",
                configuration.getHostName(), configuration.getHttpsPort(), MBEANS_ENDPOINT, applicationName);

        log.fine(restEndpoint);
        String status = "";
        try {
            String jsonResponse = executor.execute(Request.Get(restEndpoint)).returnContent().asString();
            status = parseJsonResponse(jsonResponse);
        } catch (ClientProtocolException e) {
            log.severe("Error occurred while checking if application " + applicationName + " is already started " + e);
            status = "error";
        } catch (IOException e) {
            log.severe("Error occurred while checking if application " + applicationName + " is already started " + e);
            status = "error";
        }

        if (log.isLoggable(Level.FINER)) {
            log.exiting(className, "isApplicationStarted");
        }

        if (STARTED.equals(status)) {
            log.fine("Application is started");
            return true;
        } else {
            return false;
        }
    }

    /*
     * Get the state response from the json returned
     */
    private String parseJsonResponse(String jsonString) {
        ObjectMapper mapper = new ObjectMapper();
        String status = "";
        try {
            Map result = mapper.readValue(jsonString.getBytes(), Map.class);
            status = (String) result.get("value");
        } catch (JsonParseException e) {
            log.severe("Error parsing Json response " + e);
        } catch (JsonMappingException e) {
            log.severe("Error mapping Json response " + e);
        } catch (IOException e) {
            log.severe("IOException while parsing Json response " + e);
        }
        return status;
    }

}
