package org.jboss.arquillian.container.was.wlp_remote_8_5.client;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.http.HttpHost;
import org.apache.http.HttpVersion;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.was.wlp_remote_8_5.WLPRemoteContainerConfiguration;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class WlpRestClient {

    private static final String className = WlpRestClient.class.getName();

    private static Logger log = Logger.getLogger(className);

    private WLPRemoteContainerConfiguration configuration;

    private static final String FILE_ENDPOINT = "/IBMJMXConnectorREST/file/";
    private static final String MBEANS_ENDPOINT = "/IBMJMXConnectorREST/mbeans/";

    public WlpRestClient(WLPRemoteContainerConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * 
     * @param archive
     * @throws ClientProtocolException
     * @throws IOException
     */
    public void deploy(File archive) throws ClientProtocolException, IOException {
        Executor executor = Executor.newInstance().auth(new HttpHost(configuration.getHostName()),
                configuration.getUsername(), configuration.getPassword());

        String serverRestEndpoint = "https://" + configuration.getHostName() + ":" + configuration.getHttpsPort()
                + FILE_ENDPOINT;

        byte[] result = executor
                .execute(
                        Request.Post(serverRestEndpoint).useExpectContinue().version(HttpVersion.HTTP_1_1)
                                .bodyFile(archive, ContentType.DEFAULT_BINARY)).returnContent().asBytes();

        System.out.println(new String(result));
    }

    /**
     * 
     * @param archive
     * @throws ClientProtocolException
     * @throws IOException
     */
    public void undeploy(File archive) throws ClientProtocolException, IOException {
        
        if (log.isLoggable(Level.FINER)) {
            log.entering(className, "undeploy");
        }
        
        String deployPath = configuration.getWlpHome()+"/usr/servers/"+configuration.getServerName()+"/dropins/"+archive.getName();
                
        Executor executor = Executor.newInstance().auth(new HttpHost(configuration.getHostName()),
                configuration.getUsername(), configuration.getPassword());

        String serverRestEndpoint = "https://" + configuration.getHostName() + ":" + configuration.getHttpsPort()
                + FILE_ENDPOINT  +URLEncoder.encode(deployPath)+"?recursiveDelete=true";

        int result = executor
                .execute(Request.Delete(serverRestEndpoint).useExpectContinue().version(HttpVersion.HTTP_1_1))
                .returnResponse().getStatusLine().getStatusCode();
        
        System.out.println("Undeploy result "+serverRestEndpoint+" "+result);
        
        if (log.isLoggable(Level.FINER)) {
            log.exiting(className, "isServerUp", result);
        }
        
    }

    /**
     * 
     * @return
     */
    public boolean isServerUp() throws ClientProtocolException, IOException {
        if (log.isLoggable(Level.FINER)) {
            log.entering(className, "isServerUp");
        }
        
        String hostName = "https://" + configuration.getHostName() + ":" + configuration.getHttpsPort()
                + "/IBMJMXConnectorREST";
        Executor executor = Executor.newInstance().auth(new HttpHost(configuration.getHostName()),
                configuration.getUsername(), configuration.getPassword());

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
     * 
     * @param applicationName
     * @return
     * @throws DeploymentException
     */
    public boolean isApplicationStarted(String applicationName) throws DeploymentException {

        if (log.isLoggable(Level.FINER)) {
            log.entering(className, "isApplicationStarted");
        }

        Executor executor = Executor.newInstance().auth(new HttpHost(configuration.getHostName()),
                configuration.getUsername(), configuration.getPassword());

        String hostName = "https://" + configuration.getHostName() + ":" + configuration.getHttpsPort()
                + MBEANS_ENDPOINT + "WebSphere:service=com.ibm.websphere.application.ApplicationMBean,name="
                + applicationName + "/attributes/State";

        try {
            String jsonResponse = executor.execute(Request.Get(hostName)).returnContent().asString();
            String status = parseJsonResponse(jsonResponse);
            if ("STARTED".equals(status)) {
                return true;
            }
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (log.isLoggable(Level.FINER)) {
            log.exiting(className, "isApplicationStarted");
        }
        return false;
    }

    private String parseJsonResponse(String jsonString) {
        ObjectMapper mapper = new ObjectMapper();
        String status = "";
        try {
            Map result = mapper.readValue(jsonString.getBytes(), Map.class);
            status = (String) result.get("value");
        } catch (JsonParseException e) {
            e.printStackTrace();
        } catch (JsonMappingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return status;
    }

    public static void main(String[] args) throws ClientProtocolException, IOException, DeploymentException {
        File f = new File(
                "/home/tony/git-projects/websphere-stuff/prmui-promotion-service/ear/target/prmui-promotion-service-ear.ear");
        WLPRemoteContainerConfiguration config = new WLPRemoteContainerConfiguration();
        config.setHttpsPort(9443);
        config.setHostName("localhost");
        config.setUsername("admin");
        config.setPassword("admin");
        config.setServerName("defaultServer");
        config.setWlpHome("/home/tony/software/wlp-8.5.5.4/wlp");
        WlpRestClient client = new WlpRestClient(config);
        client.isServerUp();

        client.isApplicationStarted("prmui-promotion-service-ear");

        // String destPath =
        // URLEncoder.encode("/home/tony/software/wlp-8.5.5.4/wlp/usr/servers/defaultServer/dropins/"
        // + f.getName());
        //
        client.undeploy(f);
        // destPath);
    }

}
