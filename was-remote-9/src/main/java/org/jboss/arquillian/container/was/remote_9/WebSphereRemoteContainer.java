/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009-2011, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.arquillian.container.was.remote_9;

import java.io.File;
import java.io.StringReader;
import java.lang.IllegalStateException;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.InstanceNotFoundException;
import javax.management.MalformedObjectNameException;
import javax.management.NotificationFilterSupport;
import javax.management.ObjectName;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.jboss.arquillian.container.spi.client.container.DeployableContainer;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.spi.client.container.LifecycleException;
import org.jboss.arquillian.container.spi.client.protocol.ProtocolDescription;
import org.jboss.arquillian.container.spi.client.protocol.metadata.HTTPContext;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.arquillian.container.spi.client.protocol.metadata.Servlet;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.descriptor.api.Descriptor;
import org.jboss.shrinkwrap.descriptor.api.Descriptors;
import org.jboss.shrinkwrap.descriptor.api.application6.ApplicationDescriptor;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.ibm.websphere.management.AdminClient;
import com.ibm.websphere.management.AdminClientFactory;
import com.ibm.websphere.management.application.AppConstants;
import com.ibm.websphere.management.application.AppManagement;
import com.ibm.websphere.management.application.AppManagementProxy;
import com.ibm.websphere.management.application.AppNotification;
import com.ibm.websphere.management.application.client.AppDeploymentController;
import com.ibm.websphere.management.configservice.ConfigServiceHelper;
import com.ibm.websphere.management.configservice.ConfigServiceProxy;
import com.ibm.websphere.management.exception.ConfigServiceException;
import com.ibm.websphere.management.exception.ConnectorException;

/**
 * WebSphereRemoteContainer
 *
 * @author <a href="mailto:aslak@redhat.com">Aslak Knutsen</a>
 * @author <a href="mailto:gerhard.poul@gmail.com">Gerhard Poul</a>
 * @version $Revision: $
 */
public class WebSphereRemoteContainer implements DeployableContainer<WebSphereRemoteContainerConfiguration>
{
   //-------------------------------------------------------------------------------------||
   // Instance Members -------------------------------------------------------------------||
   //-------------------------------------------------------------------------------------||
   
   private static final String className = WebSphereRemoteContainer.class.getName();
   
   private static Logger log = Logger.getLogger(className);
   
   private WebSphereRemoteContainerConfiguration containerConfiguration;

   private AdminClient adminClient;

   //-------------------------------------------------------------------------------------||
   // Required Implementations - DeployableContainer -------------------------------------||
   //-------------------------------------------------------------------------------------||

   /* (non-Javadoc)
    * @see org.jboss.arquillian.spi.DeployableContainer#setup(org.jboss.arquillian.spi.Context, org.jboss.arquillian.spi.Configuration)
    */
   public void setup(WebSphereRemoteContainerConfiguration configuration)
   {
      if (log.isLoggable(Level.FINER)) {
            log.entering(className, "setup");
      }
	   
      this.containerConfiguration = configuration;
	   
      if (log.isLoggable(Level.FINER)) {
         log.exiting(className, "setup");
      }
   }

   /* (non-Javadoc)
    * @see org.jboss.arquillian.spi.DeployableContainer#start(org.jboss.arquillian.spi.Context)
    */
   public void start() throws LifecycleException
   {
      if (log.isLoggable(Level.FINER)) {
         log.entering(className, "start");
      }
      
      Properties wasServerProps = new Properties();
      wasServerProps.setProperty(AdminClient.CONNECTOR_HOST, containerConfiguration.getRemoteServerAddress());
      wasServerProps.setProperty(AdminClient.CONNECTOR_PORT, String.valueOf(containerConfiguration.getRemoteServerSoapPort()));
      wasServerProps.setProperty(AdminClient.CONNECTOR_TYPE, AdminClient.CONNECTOR_TYPE_SOAP);
      wasServerProps.setProperty(AdminClient.USERNAME, containerConfiguration.getUsername());
      
      if (containerConfiguration.getSecurityEnabled())
      {
         wasServerProps.setProperty(AdminClient.CONNECTOR_SECURITY_ENABLED, "true");
         wasServerProps.setProperty(AdminClient.PASSWORD, containerConfiguration.getPassword());
         wasServerProps.setProperty(AdminClient.CACHE_DISABLED, "false"); 
         wasServerProps.setProperty("javax.net.ssl.trustStore", containerConfiguration.getSslTrustStore());
         wasServerProps.setProperty("javax.net.ssl.keyStore", containerConfiguration.getSslKeyStore());
         wasServerProps.setProperty("javax.net.ssl.trustStorePassword", containerConfiguration.getSslTrustStorePassword());
         wasServerProps.setProperty("javax.net.ssl.keyStorePassword", containerConfiguration.getSslKeyStorePassword());
         if (containerConfiguration.getSslTrustStoreType() != null)
            wasServerProps.setProperty("javax.net.ssl.trustStoreType", containerConfiguration.getSslTrustStoreType());
         if (containerConfiguration.getSslKeyStoreType() != null)
            wasServerProps.setProperty("javax.net.ssl.keyStoreType", containerConfiguration.getSslKeyStoreType());
      } else {
         wasServerProps.setProperty(AdminClient.CONNECTOR_SECURITY_ENABLED, "false");
      }
      
      try
      {
         adminClient = AdminClientFactory.createAdminClient(wasServerProps);
         
         ObjectName serverMBean = adminClient.getServerMBean();
         String processType = serverMBean.getKeyProperty("processType");
         
         log.fine("CanonicalKeyPropertyListString: " + serverMBean.getCanonicalKeyPropertyListString());
         
         if (processType.equals("DeploymentManager")
               || processType.equals("NodeAgent")
               || processType.equals("ManagedProcess"))
            throw new IllegalStateException("Connecting to a " + processType + " is not supported.");
      } 
      catch (Exception e) 
      {
         throw new LifecycleException("Could not create AdminClient: " + e.getMessage(), e);
      }
      
      if (log.isLoggable(Level.FINER)) {
         log.exiting(className, "start");
      }
   }

   /* (non-Javadoc)
    * @see org.jboss.arquillian.spi.DeployableContainer#deploy(org.jboss.arquillian.spi.Context, org.jboss.shrinkwrap.api.Archive)
    */
   public ProtocolMetaData deploy(final Archive<?> archive) throws DeploymentException
   {
      if (log.isLoggable(Level.FINER)) {
         log.entering(className, "deploy");
         
         log.finer("Archive provided to deploy method: " + archive.toString(true));
      }
      
      File exportedArchiveLocation = null;
      ProtocolMetaData metaData = null;
      EnterpriseArchive deploymentArchive = null;
      
      // Create an EAR file from the provided Archive that can be processed by AppDeploymentController
      if (WebArchive.class.isInstance(archive)) {
         // Packaging a single WAR file into an EAR
         String earName = archive.getName().substring(0, archive.getName().lastIndexOf("."));
         log.fine("Creating an EnterpriseArchive " + earName + ".ear from provided WebArchive " + archive.getName() + ".");

         // Create ShrinkWrap EnterpriseArchive and add the WAR file as a module
         deploymentArchive = ShrinkWrap.create(EnterpriseArchive.class, earName + ".ear")
            .addAsModule(archive);

         // Generate the application.xml DD and add it to the EAR
         ApplicationDescriptor appDescriptor = Descriptors.create(ApplicationDescriptor.class);
         appDescriptor.createModule().getOrCreateWeb().webUri(archive.getName()).contextRoot(earName);
         deploymentArchive.setApplicationXML(
               new StringAsset(appDescriptor.exportAsString()));
      } else if (EnterpriseArchive.class.isInstance(archive)){
         // Use the provided EnterpriseArchive as-is
         deploymentArchive = (EnterpriseArchive) archive;
      } else {
         throw new DeploymentException("Unsupported archive type has been provided for deployment: " + archive.getClass().getName());
      }

      String appName = createDeploymentName(deploymentArchive.getName());
      String appExtension = createDeploymentExtension(deploymentArchive.getName());
      
      try
      {
         exportedArchiveLocation = File.createTempFile(appName, appExtension);
         deploymentArchive.as(ZipExporter.class).exportTo(exportedArchiveLocation, true);
         
         Hashtable<Object, Object> prefs = new Hashtable<Object, Object>();
         
         prefs.put(AppConstants.APPDEPL_LOCALE, Locale.getDefault());
         prefs.put(AppConstants.APPDEPL_CLASSLOADINGMODE, containerConfiguration.getDeploymentClassLoadingMode());
         prefs.put(AppConstants.APPDEPL_CLASSLOADERPOLICY, containerConfiguration.getDeploymentClassLoaderPolicy());

         log.fine(String.format("Deploying with classloading mode %s",
                 containerConfiguration.getDeploymentClassLoadingMode()));
         log.fine(String.format("Deploying with classloader policy %s",
                 containerConfiguration.getDeploymentClassLoaderPolicy()));

         Properties props = new Properties();
         prefs.put (AppConstants.APPDEPL_DFLTBNDG, props);
         props.put (AppConstants.APPDEPL_DFLTBNDG_VHOST, "default_host");

         // Prepare application for deployment to WebSphere Application Server
         AppDeploymentController controller = AppDeploymentController
         	.readArchive(exportedArchiveLocation.getAbsolutePath(), prefs);

         String[] validationResult = controller.validate();
         if (validationResult != null && validationResult.length > 0) {
            throw new DeploymentException("Unable to complete all task data for deployment preparation. Reason: " + Arrays.toString(validationResult));
         }
         
         controller.saveAndClose();
         
         if (log.isLoggable(Level.FINER)) {
            // Log the contents of the saved archive from AppDeploymentController
            Archive<JavaArchive> savedArchive = ShrinkWrap.createFromZipFile(JavaArchive.class, exportedArchiveLocation);
            log.finer("Archive prepared for deployment: " + savedArchive.toString(true));            
         }
         
         Hashtable<Object, Object> module2Server = new Hashtable<Object, Object>();
         ObjectName serverMBean = adminClient.getServerMBean();
         
         String targetServer = "WebSphere:cell=" + serverMBean.getKeyProperty("cell")
                              + ",node=" + serverMBean.getKeyProperty("node")
                              + ",server=" + serverMBean.getKeyProperty("process");
         
         log.info("Target server for deployment is " + targetServer);
   
         module2Server.put("*",targetServer);
         
         prefs.put(AppConstants.APPDEPL_MODULE_TO_SERVER, module2Server);
         prefs.put(AppConstants.APPDEPL_ARCHIVE_UPLOAD, containerConfiguration.isArchiveUploadEnabled());
         
         AppManagement appManagementProxy = AppManagementProxy.getJMXProxyForClient(adminClient);
         
         NotificationFilterSupport filterSupport = new NotificationFilterSupport();
         filterSupport.enableType(AppConstants.NotificationType);
         DeploymentNotificationListener listener = new DeploymentNotificationListener(
                  adminClient, 
                  filterSupport, 
                  "Install " + appName,
                  AppNotification.INSTALL);
         
         appManagementProxy.installApplication(
               exportedArchiveLocation.getAbsolutePath(),
               appName, 
               prefs,
               null);
         
         synchronized(listener) 
         {
            listener.wait();
         }

         if(!listener.isSuccessful())
            throw new IllegalStateException("Application not sucessfully deployed: " + listener.getMessage());            

         DeploymentNotificationListener distributionListener = null;
         int checkCount = 0;
         while (checkDistributionStatus(distributionListener) != AppNotification.DISTRIBUTION_DONE
               && ++checkCount < 300)
         {
            Thread.sleep(1000);
            
            distributionListener = new DeploymentNotificationListener(
                  adminClient,
                  filterSupport,
                  null,
                  AppNotification.DISTRIBUTION_STATUS_NODE);
            
            synchronized(distributionListener)
            {
               appManagementProxy.getDistributionStatus(appName, new Hashtable<Object, Object>(), null);
               distributionListener.wait();
            }
         }

         if (checkCount < 300)
         {
            String targetsStarted = appManagementProxy.startApplication(appName, null, null);
            log.info("Application was started on the following targets: " + targetsStarted);
            if (targetsStarted == null)
               throw new IllegalStateException("Start of the application was not successful. WAS JVM logs should contain the detailed error message.");
         } else {
            throw new IllegalStateException("Distribution of application did not succeed to all nodes.");
         }
         
         metaData = discoverProtocolMetaDataFromConfiguration(adminClient, 
               serverMBean.getKeyProperty("node"),
               serverMBean.getKeyProperty("process"),
               appName);
      } 
      catch (Exception e) 
      {
         throw new DeploymentException("Could not deploy application", e);
      }
      finally
      {
         if(exportedArchiveLocation != null) 
         {  
            exportedArchiveLocation.delete();
         }
      }
      
      if (log.isLoggable(Level.FINER)) {
         log.exiting(className, "deploy");
      }
      
      return metaData;
   }
   
   @SuppressWarnings("rawtypes")
   private ProtocolMetaData discoverProtocolMetaDataFromConfiguration(AdminClient adminClient, String targetNode, String targetProcess, String appName) throws InstanceNotFoundException, ConnectorException, ConfigServiceException {
      ProtocolMetaData metaData = new ProtocolMetaData();
      String remoteServerAddress = null;
      int remoteServerHttpPort = 0;
      
      ConfigServiceProxy configServiceProxy = new ConfigServiceProxy(adminClient);
      
      ObjectName nodeObjectName = ConfigServiceHelper.createObjectName(null, "Node");
      ObjectName[] nodeObjectNames = configServiceProxy.queryConfigObjects(null, null, nodeObjectName, null);
      ObjectName targetNodeObjectName = null;
      
      for (ObjectName node : nodeObjectNames) {
         String nodeName = (String) configServiceProxy.getAttribute(null, node, "name");
         if (nodeName.equals(targetNode)) {
            targetNodeObjectName = node;
            remoteServerAddress = (String) configServiceProxy.getAttribute(null, targetNodeObjectName, 
                  "hostName");
         }
      }
      
      if (remoteServerAddress == null || targetNodeObjectName == null)
         throw new InstanceNotFoundException("Target node " + targetNode + " was not found.");
      
      ObjectName serverEntries = ConfigServiceHelper.createObjectName(null, "ServerEntry");
      ObjectName[] serverEntryObjectNames = configServiceProxy.queryConfigObjects(null, 
            targetNodeObjectName, serverEntries, null);
      
      for (ObjectName serverEntry : serverEntryObjectNames) {
         String serverName = (String) configServiceProxy.getAttribute(null, serverEntry, "serverName");
         if (serverName.equals(targetProcess)) {
            List specialEndpoints = (List) configServiceProxy.getAttribute(null, serverEntry, 
                  "specialEndpoints");
            remoteServerHttpPort = getEndpointPort(specialEndpoints, "WC_defaulthost");
         }
      }
      
      log.fine("Generating HTTPContext: " + remoteServerAddress + ", " + remoteServerHttpPort);
      HTTPContext httpContext = new HTTPContext(remoteServerAddress, remoteServerHttpPort);
      
      try {
         Set applicationObjectNameSet = adminClient.queryNames(
               new ObjectName("WebSphere:type=J2EEApplication,name=" + appName + ",*"), null);
         if (applicationObjectNameSet.isEmpty())
            throw new InstanceNotFoundException("Unable to find application in JMX: " + appName);
         
         ObjectName applicationObjectName = (ObjectName)applicationObjectNameSet.iterator().next();
         String applicationDD = (String)adminClient.getAttribute(applicationObjectName, "deploymentDescriptor");

         log.fine("applicationDD: " + applicationDD);
         
         XPath xpath = XPathFactory.newInstance().newXPath();
         xpath.setNamespaceContext(new JavaEENamespaceContext());
         NodeList webModules = (NodeList) xpath.evaluate("/javaee:application/javaee:module/javaee:web", 
             new InputSource(new StringReader(applicationDD)), XPathConstants.NODESET);

         if (webModules.getLength() == 0){ //search J2EE 1.4 XML Schemas for backwards compatibility
            webModules = (NodeList) xpath.evaluate("/j2ee:application/j2ee:module/j2ee:web",
                    new InputSource(new StringReader(applicationDD)), XPathConstants.NODESET);
         }

         if (webModules.getLength() == 0){ //search no-namespace for DTD-based descriptor for backwards compatibility
            webModules = (NodeList) xpath.evaluate("/application/module/web",
                    new InputSource(new StringReader(applicationDD)), XPathConstants.NODESET);
         }

         for (int i=0; i < webModules.getLength(); i++) {
            Node webModule = webModules.item(i);
            
            String weburi="", contextroot="";
            NodeList webModuleChildNodes = webModule.getChildNodes();
            for (int j=0; j < webModuleChildNodes.getLength(); j++) {
               Node webModuleChild = webModuleChildNodes.item(j);
               if (webModuleChild.getNodeName().equals("web-uri"))
                  weburi = webModuleChild.getTextContent();
               if (webModuleChild.getNodeName().equals("context-root"))
                  contextroot = webModuleChild.getTextContent();
            }
            
            // Now look up the currentModule and figure out its servlets
            
            Set webmoduleObjectNameSet = adminClient.queryNames(
                  new ObjectName("WebSphere:type=WebModule,name=" + weburi + ",*"), null);
            if (webmoduleObjectNameSet.isEmpty())
               throw new IllegalStateException("Unable to find web module in JMX: " + weburi);
            
            ObjectName webmoduleObjectName = (ObjectName)webmoduleObjectNameSet.iterator().next();
            String webmoduleDD = (String)adminClient.getAttribute(webmoduleObjectName, "deploymentDescriptor");
            
            log.fine("webmoduleDD: " + webmoduleDD);
   
            xpath = XPathFactory.newInstance().newXPath();
            xpath.setNamespaceContext(new JavaEENamespaceContext());
            NodeList servletMappings = (NodeList) xpath.evaluate("/javaee:web-app/javaee:servlet-mapping",
                new InputSource(new StringReader(webmoduleDD)), XPathConstants.NODESET);

            if (servletMappings.getLength() == 0) { //search J2EE 1.4 XML Schemas for backwards compatibility
               servletMappings = (NodeList) xpath.evaluate("/j2ee:web-app/j2ee:servlet-mapping",
                       new InputSource(new StringReader(webmoduleDD)), XPathConstants.NODESET);
            }

            if (servletMappings.getLength() == 0) { //search no-namespace for DTD-based descriptor for backwards compatibility
               servletMappings = (NodeList) xpath.evaluate("/web-app/servlet-mapping",
                       new InputSource(new StringReader(webmoduleDD)), XPathConstants.NODESET);
            }

            for (int j=0; j < servletMappings.getLength(); j++) {
               Node servletMapping = servletMappings.item(j);
               NodeList servletMappingChildNodes = servletMapping.getChildNodes();
               String servletName = null;
               for (int k=0; k < servletMappingChildNodes.getLength(); k++) {
                  Node childNode = servletMappingChildNodes.item(k);
                  if (childNode.getNodeName().equals("url-pattern"))
                     servletName = childNode.getTextContent().replaceFirst("/", "");
               }
               if (servletName != null) {
                  log.fine("Adding servlet to context: " + servletName + ", " + contextroot);
                  httpContext.add(new Servlet(servletName, contextroot));
               } else {
                  log.warning("Unable to find servlet-name in web-module " + weburi + " deployment descriptor");
               }
            }
         }
      } catch (Exception e) {
         log.log(Level.SEVERE, "Error while processing the deployment descriptor", e);
      }
      
      metaData.addContext(httpContext);

      return metaData;
   }

   @SuppressWarnings("rawtypes")
   private int getEndpointPort(List specialEndpoints, String endPointIdentifier) {
      for (Object specialEndpoint : specialEndpoints) {
         AttributeList specialEndpointAttributeList = (AttributeList)specialEndpoint;
         String endPointName = (String)getAttributeByName(specialEndpointAttributeList, "endPointName");
         if (endPointName.equals(endPointIdentifier)) {
            AttributeList endpointAttributeList = 
               (AttributeList)getAttributeByName(specialEndpointAttributeList, "endPoint");
            return (Integer)getAttributeByName(endpointAttributeList, "port");
         }
      }
      return 0;
   }
   
   private Object getAttributeByName(AttributeList attrList, String name) {
      for (Object attrObject : attrList) {
         Attribute attr = (Attribute)attrObject;
         if (attr.getName().equals(name))
            return attr.getValue();
      }
      return null;
   }

   /*
    * Checks the listener and figures out the aggregate distribution status of all nodes
    */
   private String checkDistributionStatus(DeploymentNotificationListener listener) throws MalformedObjectNameException, NullPointerException, IllegalStateException {
      String distributionState = AppNotification.DISTRIBUTION_UNKNOWN;
      if (listener != null)
      {
        String compositeStatus = listener.getNotificationProps()
           .getProperty(AppNotification.DISTRIBUTION_STATUS_COMPOSITE);
        if (compositeStatus != null)
        {
           log.finer("compositeStatus: " + compositeStatus);
           String[] serverStati = compositeStatus.split("\\+");
           int countTrue = 0, countFalse = 0, countUnknown = 0;
           for (String serverStatus : serverStati)
           {
              ObjectName objectName = new ObjectName(serverStatus);
              distributionState = objectName.getKeyProperty("distribution");
              log.finer("distributionState: " + distributionState);
              if (distributionState.equals("true"))
                 countTrue++;
              if (distributionState.equals("false"))
                 countFalse++;
              if (distributionState.equals("unknown"))
                 countUnknown++;
           }
           if (countUnknown > 0)
           {
              distributionState = AppNotification.DISTRIBUTION_UNKNOWN;
           } else if (countFalse > 0) {
              distributionState = AppNotification.DISTRIBUTION_NOT_DONE;
           } else if (countTrue > 0) {
              distributionState = AppNotification.DISTRIBUTION_DONE;
           } else {
              throw new IllegalStateException("Reported distribution status is invalid.");
           }
        }
      }
      return distributionState;
   }

   /* (non-Javadoc)
    * @see org.jboss.arquillian.spi.DeployableContainer#undeploy(org.jboss.arquillian.spi.Context, org.jboss.shrinkwrap.api.Archive)
    */
   public void undeploy(final Archive<?> archive) throws DeploymentException
   {
      if (log.isLoggable(Level.FINER)) {
         log.entering(className, "undeploy");
      }
      
      String appName = createDeploymentName(archive.getName());
      
      try
      {
//         Session configSession = new Session(containerConfiguraiton.getUsername(), false);
//         ConfigServiceProxy configProxy = new ConfigServiceProxy(adminClient);

         Hashtable<Object, Object> prefs = new Hashtable<Object, Object>();

         NotificationFilterSupport filterSupport = new NotificationFilterSupport();
         filterSupport.enableType(AppConstants.NotificationType);
         DeploymentNotificationListener listener = new DeploymentNotificationListener(
                  adminClient, 
                  filterSupport, 
                  "Uninstall " + appName,
                  AppNotification.UNINSTALL);
         
         AppManagement appManagementProxy = AppManagementProxy.getJMXProxyForClient(adminClient);
         
         appManagementProxy.uninstallApplication(
               appName, 
               prefs,
               null);
//               configSession.getSessionId());
         
         synchronized(listener) 
         {
            listener.wait();
         }
         if(listener.isSuccessful())
         {
            //configProxy.save(configSession, true);
         }
         else
         {
            throw new IllegalStateException("Application not sucessfully undeployed: " + listener.getMessage());
            //configProxy.discard(configSession);
         }
      } 
      catch (Exception e) 
      {
         throw new DeploymentException("Could not undeploy application", e);
      }
      
      if (log.isLoggable(Level.FINER)) {
         log.exiting(className, "undeploy");
      }
   }

   /* (non-Javadoc)
    * @see org.jboss.arquillian.spi.DeployableContainer#stop(org.jboss.arquillian.spi.Context)
    */
   public void stop() throws LifecycleException
   {
      if (log.isLoggable(Level.FINER)) {
         log.entering(className, "stop");
      }
      
      if (log.isLoggable(Level.FINER)) {
         log.exiting(className, "stop");
      }
   }

   //-------------------------------------------------------------------------------------||
   // Internal Helper Methods ------------------------------------------------------------||
   //-------------------------------------------------------------------------------------||
   
   private String createDeploymentName(String archiveName) 
   {
      return archiveName.substring(0, archiveName.lastIndexOf("."));
   }

   private String createDeploymentExtension(String archiveName) 
   {
      return archiveName.substring(archiveName.lastIndexOf("."));
   }

	public Class<WebSphereRemoteContainerConfiguration> getConfigurationClass() {
		// TODO Auto-generated method stub
		return WebSphereRemoteContainerConfiguration.class;
	}
	
	public ProtocolDescription getDefaultProtocol() {
      if (log.isLoggable(Level.FINER)) {
         log.entering(className, "getDefaultProtocol");
      }
      
      if (log.isLoggable(Level.FINER)) {
         log.exiting(className, "getDefaultProtocol");
      }
      
		return new ProtocolDescription("Servlet 3.0");
	}
	
	public void deploy(Descriptor descriptor) throws DeploymentException {
		// TODO Auto-generated method stub
		
	}
	
	public void undeploy(Descriptor descriptor) throws DeploymentException {
		// TODO Auto-generated method stub
		
	}
}
