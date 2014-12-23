package org.jboss.arquillian.container.was.wlp_remote_8_5;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;

import org.apache.http.client.ClientProtocolException;
import org.jboss.arquillian.container.spi.client.container.DeployableContainer;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.spi.client.container.LifecycleException;
import org.jboss.arquillian.container.spi.client.protocol.ProtocolDescription;
import org.jboss.arquillian.container.spi.client.protocol.metadata.HTTPContext;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.arquillian.container.spi.client.protocol.metadata.Servlet;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.descriptor.api.Descriptor;

/**
 * 
 * @author Tony Ayres
 *
 */
public class WLPRemoteContainer implements DeployableContainer<WLPRemoteContainerConfiguration> {

    private static final String className = WLPRemoteContainer.class.getName();

    private static Logger log = Logger.getLogger(className);

    private WLPRemoteContainerConfiguration containerConfiguration;

    private WLPRestClient restClient;

    @Override
    public Class<WLPRemoteContainerConfiguration> getConfigurationClass() {
        return WLPRemoteContainerConfiguration.class;
    }

    @Override
    public void setup(WLPRemoteContainerConfiguration configuration) {
        if (log.isLoggable(Level.FINER)) {
            log.entering(className, "setup");
        }

        this.containerConfiguration = configuration;
        restClient = new WLPRestClient(containerConfiguration);

        if (log.isLoggable(Level.FINER)) {
            log.exiting(className, "setup");
        }
    }

    @Override
    public void start() throws LifecycleException {
        
        boolean ready;
        
        try {
            ready = restClient.isServerUp();
        } catch (ClientProtocolException e) {
            throw new LifecycleException("Could not determine remove server status : "+e);
        } catch (IOException e) {
            throw new LifecycleException("Could not determine remove server status : "+e);
        }
        
        if (!ready) {
            throw new LifecycleException("Remote server is not started");
        }
    }

    @Override
    public void stop() throws LifecycleException {
        // TODO Auto-generated method stub

    }

    @Override
    public ProtocolDescription getDefaultProtocol() {
        if (log.isLoggable(Level.FINER)) {
            log.entering(className, "getDefaultProtocol");
        }

        String defaultProtocol = "Servlet 3.0";

        if (log.isLoggable(Level.FINER)) {
            log.exiting(className, "getDefaultProtocol", defaultProtocol);
        }

        return new ProtocolDescription(defaultProtocol);
    }

    @Override
    public ProtocolMetaData deploy(Archive<?> archive) throws DeploymentException {

        String archiveName = archive.getName();
        String archiveType = createDeploymentType(archiveName);
        String deployName = createDeploymentName(archiveName);

        if (!archiveType.equalsIgnoreCase("ear") && !archiveType.equalsIgnoreCase("war")
                && !archiveType.equalsIgnoreCase("eba")) {

            throw new DeploymentException("Invalid archive type: " + archiveType
                    + ".  Valid archive types are ear, war, and eba.");
        }

        String appDir = getAppDirectory();
        File exportedArchiveLocation = new File(appDir, archiveName);

        archive.as(ZipExporter.class).exportTo(exportedArchiveLocation, true);

        try {
            restClient.deploy(exportedArchiveLocation);
        } catch (Exception e) {
            throw new DeploymentException(e.getMessage());
        }

        // Wait until the application is deployed and available
        waitForApplicationTargetState(deployName, true, containerConfiguration.getAppDeployTimeout());

        // Return metadata on how to contact the deployed application
        ProtocolMetaData metaData = new ProtocolMetaData();
        HTTPContext httpContext = new HTTPContext(containerConfiguration.getHostName(),
                containerConfiguration.getHttpPort());
        httpContext.add(new Servlet("ArquillianServletRunner", deployName));
        metaData.addContext(httpContext);

        if (log.isLoggable(Level.FINER)) {
            log.exiting(className, "deploy");
        }

        return metaData;
    }

    @Override
    public void undeploy(Archive<?> archive) throws DeploymentException {
        if (log.isLoggable(Level.FINER)) {
            log.entering(className, "undeploy");
        }
        
        String archiveName = archive.getName();
        String appDir = getAppDirectory();
        File exportedArchiveLocation = new File(appDir, archiveName);
        try {
            restClient.undeploy(exportedArchiveLocation);
        } catch (Exception e) {
            throw new DeploymentException("Error undeploying application "+archiveName+" "+e);
        }
        
        if (log.isLoggable(Level.FINER)) {
            log.exiting(className, "undeploy");
        }

    }

    @Override
    public void deploy(Descriptor descriptor) throws DeploymentException {
        // TODO Auto-generated method stub

    }

    @Override
    public void undeploy(Descriptor descriptor) throws DeploymentException {
        // TODO Auto-generated method stub

    }

    private String createDeploymentName(String archiveName) {
        return archiveName.substring(0, archiveName.lastIndexOf("."));
    }

    private String createDeploymentType(String archiveName) {
        return archiveName.substring(archiveName.lastIndexOf(".") + 1);
    }

    private String getAppDirectory() {
        String appDir = containerConfiguration.getWlpHome() + "/usr/servers/" + containerConfiguration.getServerName()
                + "/dropins";
        if (log.isLoggable(Level.FINER))
            log.finer("appDir: " + appDir);
        return appDir;
    }

    private void waitForApplicationTargetState(String applicationName, boolean targetState, int timeout)
            throws DeploymentException {
        if (log.isLoggable(Level.FINER)) {
            log.entering(className, "waitForMBeanTargetState");
        }
        try {
            int timeleft = timeout * 1000;
            boolean ready = false;
            while (!ready) {
                Thread.sleep(100);
                ready = restClient.isApplicationStarted(applicationName);
                if (timeleft <= 0)
                    throw new DeploymentException("Timeout while waiting for ApplicationState to reach STARTED");
                timeleft -= 100;
            }
        } catch (InterruptedException e) {         
            throw new DeploymentException("Error occurred while while waiting for ApplicationState to reach STARTED "+e);
        }

        if (log.isLoggable(Level.FINER)) {
            log.exiting(className, "waitForMBeanTargetState");
        }
    }

}
