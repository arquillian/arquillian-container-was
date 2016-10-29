/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012, 2013, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.arquillian.container.was.wlp_managed_8_5;

import static java.util.logging.Level.FINER;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ObjectInstance;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

/**
 * WLPManagedContainer
 *
 * @author <a href="mailto:gerhard.poul@gmail.com">Gerhard Poul</a>
 * @version $Revision: $
 */
public class WLPManagedContainer implements DeployableContainer<WLPManagedContainerConfiguration>
{
   private static final String className = WLPManagedContainer.class.getName();
   
   private static Logger log = Logger.getLogger(className);
   
   private WLPManagedContainerConfiguration containerConfiguration;
   
   private JMXConnector jmxConnector;
   
   private MBeanServerConnection mbsc;
   
   private Process wlpProcess;
   
   private Thread shutdownThread;
   
   public void setup(WLPManagedContainerConfiguration configuration)
   {
      if (log.isLoggable(Level.FINER)) {
            log.entering(className, "setup");
      }
      
      this.containerConfiguration = configuration;
      
      if (log.isLoggable(Level.FINER)) {
         log.exiting(className, "setup");
      }
   }

   // This method includes parts heavily based on the ManagedDeployableContainer.java in the jboss-as
   // managed container implementation as written by Thomas.Diesler@jboss.com
   public void start() throws LifecycleException
   {
      if (log.isLoggable(Level.FINER)) {
         log.entering(className, "start");
      }

      // Find WebSphere Liberty Profile VMs by looking for ws-launch.jar and the name of the server
      String vmid;
      VirtualMachine wlpvm = null;
      String serviceURL = null;

      try {
         vmid = findVirtualMachineIdByName(containerConfiguration.getServerName());
         // If it has already been started, throw exception unless we explicitly allow connecting to a running server
         if (vmid != null) {
            if (!containerConfiguration.isAllowConnectingToRunningServer())
               throw new LifecycleException("Connecting to an already running server is not allowed");
            
            wlpvm = VirtualMachine.attach(vmid);
            
            serviceURL = getVMLocalConnectorAddress(wlpvm);
            if (serviceURL == null)
               throw new LifecycleException("Unable to retrieve connector address for localConnector");
         } else {

            if (containerConfiguration.isAddLocalConnector()) {
                // Get path to server.xml
                String serverXML = getServerXML();
                if ("defaultServer".equals(containerConfiguration.getServerName()) && !new File(serverXML).exists()) {
                    // If server.xml doesn't exist for the default server, we may be dealing with a new
                    // installation where the server will be created at first
                    // startup. Get the default template server.xml instead. The server.xml for "defaultServer"
                    // will be created from this.
                    serverXML = getDefaultServerXML();
                }
                 
                // Read server.xml file into Memory
                Document document = readServerXML(serverXML);
                 
                addFeatures(document, "localConnector-1.0");
                 
                writeServerXML(document, serverXML);
            }
             
            // Start the WebSphere Liberty Profile VM
            List<String> cmd = new ArrayList<String>();

            String javaVmArguments = containerConfiguration.getJavaVmArguments();
            
            cmd.add(System.getProperty("java.home") + "/bin/java");
            cmd.add("-Dcom.ibm.ws.logging.console.log.level=INFO");
            if (!javaVmArguments.equals(""))
               cmd.add(javaVmArguments);
            cmd.add("-javaagent:lib/bootstrap-agent.jar");
            cmd.add("-jar");
            cmd.add("lib/ws-launch.jar");
            cmd.add(containerConfiguration.getServerName());
            
            log.finer("Starting server with command: " + cmd.toString());
            
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(new File(containerConfiguration.getWlpHome()));
            pb.redirectErrorStream(true);
            wlpProcess = pb.start();
            
            new Thread(new ConsoleConsumer()).start();
            
            final Process proc = wlpProcess;
            shutdownThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    if (proc != null) {
                        proc.destroy();
                        try {
                            proc.waitFor();
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            });
            Runtime.getRuntime().addShutdownHook(shutdownThread);
            
            // Wait up to 30s for the server to start
            int startupTimeout = containerConfiguration.getServerStartTimeout() * 1000;
            while (startupTimeout > 0 && serviceURL == null) {
               startupTimeout -= 500;
               Thread.sleep(500);

               // Verify that the process we're looking for is actually running
               int ev = Integer.MIN_VALUE; // exit value of the process
               IllegalThreadStateException itse = null; // Will be thrown when process is still running
               try {
                  ev = wlpProcess.exitValue();
               } catch (IllegalThreadStateException e) {
                  itse = e;
               }

               if (itse == null)
                  throw new LifecycleException("Process terminated prematurely; ev = " + ev);
               
               if (vmid == null)
                  // Find WebSphere Liberty Profile VMs by looking for ws-launch.jar and the name of the server
                  vmid = findVirtualMachineIdByName(containerConfiguration.getServerName());
               
               if (wlpvm == null && vmid != null)
                  wlpvm = VirtualMachine.attach(vmid);
               
               if (serviceURL == null && wlpvm != null)
                  serviceURL = getVMLocalConnectorAddress(wlpvm);
            }
            
            // If serviceURL is still null, we were unable to start the virtual machine
            if (serviceURL == null)
               throw new LifecycleException("Unable to retrieve connector address for localConnector of started VM");
            
            log.finer("vmid: " + vmid);
         }
      } catch (Exception e) {
         throw new LifecycleException("Could not start container", e);
      }
      
      try {
         JMXServiceURL url = new JMXServiceURL(serviceURL);
         jmxConnector = JMXConnectorFactory.connect(url);
         mbsc = jmxConnector.getMBeanServerConnection();
      } catch (IOException e) {
         throw new LifecycleException("Connecting to the JMX MBean Server failed", e);
      }
      
      if (log.isLoggable(Level.FINER)) {
         log.exiting(className, "start");
      }
   }

   private String getVMLocalConnectorAddress(VirtualMachine wlpvm)
         throws IOException {
      String serviceURL;
      String PROPERTY_NAME = "com.sun.management.jmxremote.localConnectorAddress";
      
      serviceURL = wlpvm.getAgentProperties().getProperty(PROPERTY_NAME);
      
      // On some environments like the IBM JVM the localConnectorAddress is not
      // in the AgentProperties but in the SystemProperties.
      if (serviceURL == null)
         serviceURL = wlpvm.getSystemProperties().getProperty(PROPERTY_NAME);
      
      if (log.isLoggable(Level.FINER)) {
         log.finer("service url: " + serviceURL);
      }
      
      return serviceURL;
   }

   private String findVirtualMachineIdByName(String serverName) {
      if (log.isLoggable(Level.FINER)) {
         log.entering(className, "findVirtualMachineIdByName");
      }

      List<VirtualMachineDescriptor> vmds = VirtualMachine.list();
      for (VirtualMachineDescriptor vmd : vmds) {
         String displayName = vmd.displayName();
         if (log.isLoggable(Level.FINER)) {
            log.finer("VMD displayName: " + displayName);
            log.finer("VMD id: " + vmd.id());
         }
         if (displayName.contains(serverName) && (displayName.contains("ws-server.jar") || displayName.contains("ws-launch.jar"))) {
            // If VM's display name matches, return.
            if (log.isLoggable(Level.FINER)) {
               log.exiting(className, "findVirtualMachineIdByName", vmd.id());
            }
            return vmd.id();
         }
      }

      // Only reached when VM is not found

      if (log.isLoggable(Level.FINER)) {
         log.exiting(className, "findVirtualMachineIdByName");
      }
      
      return null;
   }

   public ProtocolMetaData deploy(final Archive<?> archive) throws DeploymentException
   {
      if (log.isLoggable(Level.FINER)) {
         log.entering(className, "deploy");
         
         log.finer("Archive provided to deploy method: " + archive.toString(true));
      }
      
      String archiveName = archive.getName();
      String archiveType = createDeploymentType(archiveName);
      String deployName = createDeploymentName(archiveName);

      try {
         // If the deployment is to server.xml, then update server.xml with the application information
         if (containerConfiguration.isDeployTypeXML()) {
            // Throw error if deployment type is not ear, war, or eba
            if (!archiveType.equalsIgnoreCase("ear") && !archiveType.equalsIgnoreCase("war") && !archiveType.equalsIgnoreCase("eba"))
               throw new DeploymentException("Invalid archive type: " + archiveType + ".  Valid archive types are ear, war, and eba.");

            // Save the archive to disk so it can be loaded by the container.
            String appDir = getAppDirectory();
            File exportedArchiveLocation = new File(appDir, archiveName);
            archive.as(ZipExporter.class).exportTo(exportedArchiveLocation, true);

            // Read server.xml file into Memory
            Document document = readServerXML();

            // Add the archive as appropriate to the server.xml file
            addApplication(document, deployName, archiveName, archiveType);

            // Update server.xml on file system
            writeServerXML(document);
         }
         // Otherwise put the application in the dropins directory
         else {
            // Save the archive to disk so it can be loaded by the container.
            String dropInDir = getDropInDirectory();
            File exportedArchiveLocation = new File(dropInDir, archiveName);
            archive.as(ZipExporter.class).exportTo(exportedArchiveLocation, true);
         }

         // Wait until the application is deployed and available
         waitForApplicationTargetState(deployName, true, containerConfiguration.getAppDeployTimeout());

         // Return metadata on how to contact the deployed application
         ProtocolMetaData metaData = new ProtocolMetaData();
         HTTPContext httpContext = new HTTPContext("localhost", getHttpPort());
         httpContext.add(new Servlet("ArquillianServletRunner", deployName));
         metaData.addContext(httpContext);

         if (log.isLoggable(Level.FINER)) {
            log.exiting(className, "deploy");
         }

         return metaData;
      } catch (Exception e) {
         throw new DeploymentException("Exception while deploying application.", e);
      }
   }
   
   private int getHttpPort() throws DeploymentException {
      if (log.isLoggable(Level.FINER)) {
         log.entering(className, "getHttpPort");
      }
      
      int httpPort = containerConfiguration.getHttpPort();
      
      if (httpPort == 0)
         httpPort = getHttpPortFromChannelFWMBean("defaultHttpEndpoint");
      
      if (log.isLoggable(Level.FINER)) {
         log.exiting(className, "getHttpPort", httpPort);
      }
      return httpPort;
   }
   
   // Returns the HttpPort configured on the Channel Framework MBean with the provided endpoint name
   private int getHttpPortFromChannelFWMBean(String endpointName) throws DeploymentException {
      if (log.isLoggable(Level.FINER)) {
         log.entering(className, "getHttpPortFromChannelFWMBean", endpointName);
      }

      ObjectName endpointMBean = null;
      try {
         endpointMBean = new ObjectName(
               "WebSphere:feature=channelfw,type=endpoint,name="
                     + endpointName);
      } catch (MalformedObjectNameException e) {
         throw new DeploymentException(
               "The generated object name is wrong. The endpointName used was '"
                     + endpointName + "'", e);
      } catch (NullPointerException e) {
         // This should never happen given that the name parameter to the
         // ObjectName constructor above can never be null
         throw new DeploymentException("This should never happen", e);
      }
      
      int httpPort;
      
      try {
         if (!mbsc.isRegistered(endpointMBean))
            throw new DeploymentException("The Channel Framework MBean with endpointName '"
                  + endpointName + "' does not exist.");
         
         httpPort = ((Integer)mbsc.getAttribute(endpointMBean, "Port")).intValue();
         log.finer("httpPort: " + httpPort);
      } catch (Exception e) {
         throw new DeploymentException(
               "Exception while retrieving httpPort information from Channel Framework MBean. "
               + "The httpPort can also be manually configured in the arquillian container configuration.", e);
      }

      if (log.isLoggable(Level.FINER)) {
         log.exiting(className, "getHttpPortFromChannelFWMBean", httpPort);
      }
      return httpPort;
   }

   public void undeploy(final Archive<?> archive) throws DeploymentException
   {
      if (log.isLoggable(Level.FINER)) {
         log.entering(className, "undeploy");
      }
      
      String archiveName = archive.getName();
      String deployName = createDeploymentName(archiveName);

      try {
         // If deploy type is xml, then remove the application from the xml file, which causes undeploy
         if (containerConfiguration.isDeployTypeXML()) {
            // Read the server.xml file into Memory
            Document document = readServerXML();

            // Remove the archive from the server.xml file
            removeApplication(document);

            // Update server.xml on file system
            writeServerXML(document);

            // Wait until the application is undeployed
            waitForApplicationTargetState(deployName, false, containerConfiguration.getAppUndeployTimeout());

            // Remove archive from the apps directory
            String appDir = getAppDirectory();
            File exportedArchiveLocation = new File(appDir, archiveName);
            if (!exportedArchiveLocation.delete())
               throw new DeploymentException("Unable to delete archive from apps directory");
         }
         else {
            // Remove archive from the dropIn directory, which causes undeploy
            String dropInDir = getDropInDirectory();
            File exportedArchiveLocation = new File(dropInDir, archiveName);
            if (!exportedArchiveLocation.delete())
               throw new DeploymentException("Unable to delete archive from dropIn directory");

            // Wait until the application is undeployed
            waitForApplicationTargetState(deployName, false, containerConfiguration.getAppUndeployTimeout());
         }

      } catch (Exception e) {
          throw new DeploymentException("Exception while undeploying application.", e);
      }

      if (log.isLoggable(Level.FINER)) {
         log.exiting(className, "undeploy");
      }
   }

   private String getDropInDirectory() {
      String dropInDir = containerConfiguration.getWlpHome() + "/usr/servers/" +
            containerConfiguration.getServerName() + "/dropins";
      if (log.isLoggable(Level.FINER))
         log.finer("dropInDir: " + dropInDir);
      return dropInDir;
   }
   
   private String getAppDirectory()
   {
      String appDir = containerConfiguration.getWlpHome() + "/usr/servers/" +
         containerConfiguration.getServerName() + "/apps";
      if (log.isLoggable(Level.FINER))
         log.finer("appDir: " + appDir);
      return appDir;
   }

   private String getServerXML()
   {
      String serverXML = containerConfiguration.getWlpHome() + "/usr/servers/" +
         containerConfiguration.getServerName() + "/server.xml";
      if (log.isLoggable(Level.FINER))
         log.finer("server.xml: " + serverXML);
      return serverXML;
   }

   // templates/servers/defaultServer/server.xml
   private String getDefaultServerXML() {
      String serverXML = containerConfiguration.getWlpHome() + 
                         "/templates/servers/defaultServer/server.xml";
      if (log.isLoggable(FINER)) {
         log.finer("default server.xml: " + serverXML);
      }
      
      return serverXML;
   }

   private String createDeploymentName(String archiveName) 
   {
      return archiveName.substring(0, archiveName.lastIndexOf("."));
   }
   
   private String createDeploymentType(String archiveName)
   {
      return archiveName.substring(archiveName.lastIndexOf(".")+1);
   }
   
   private Document readServerXML() throws DeploymentException {
       return readServerXML(getServerXML());
   }

   private Document readServerXML(String serverXML) throws DeploymentException {
      try {
         DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
         DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
         return documentBuilder.parse(new File(serverXML));
      } catch (Exception e) {
         throw new DeploymentException("Exception while reading server.xml file.", e);
      }
   }
   
   private void writeServerXML(Document doc) throws DeploymentException {
       writeServerXML(doc, getServerXML());
   }
   
   private void writeServerXML(Document doc, String serverXML) throws DeploymentException {
      try {
         TransformerFactory tf = TransformerFactory.newInstance();
         Transformer tr = tf.newTransformer();
         tr.setOutputProperty(OutputKeys.INDENT, "yes");
         DOMSource source = new DOMSource(doc);
         StreamResult res = new StreamResult(new File(serverXML));
         tr.transform(source, res);
      } catch (Exception e) {
         throw new DeploymentException("Exception wile writing server.xml file.", e);
      }
   }
   
   private Element createFeature(Document doc, String featureName) {
       
       Element feature = doc.createElement("feature");
       feature.appendChild(doc.createTextNode(featureName));
       
       return feature;
   }
   
   private void addFeatures(Document doc, String featureNames) {
      NodeList rootList = doc.getElementsByTagName("featureManager");
      Node featureManager = rootList.item(0);
      
      for (String featureName : featureNames.split(",")) {
          if (!checkFeatureAlreadyThere(featureName, featureManager.getChildNodes())) {
              featureManager.appendChild(createFeature(doc, featureName));
          }
      }
   }
   
   private boolean checkFeatureAlreadyThere(String featureName, NodeList featureManagerList) {
       for (int i=0; i<featureManagerList.getLength(); i++) {
           Node feature = featureManagerList.item(i);
           if ("feature".equals(feature.getNodeName())) {
               Node featureText = feature.getFirstChild();
               if (featureText != null && featureText.getTextContent().trim().equals(featureName)) {
                   return true;
               }
           }
       }
       
       return false;
   }

   private Element createApplication(Document doc, String deploymentName, String archiveName, String type)
   {
      // create new Application
      Element application = doc.createElement("application");
      application.setAttribute("id", deploymentName);
      application.setAttribute("location", archiveName);
      application.setAttribute("name", deploymentName);
      application.setAttribute("type", type);

      // create shared library
      if (containerConfiguration.getSharedLib() != null) {
         Element sharedLib = doc.createElement("classloader");
         sharedLib.setAttribute("commonLibraryRef", containerConfiguration.getSharedLib());
         application.appendChild(sharedLib);
      }

      return application;
   }

   private void addApplication(Document doc, String deployName, String archiveName, String type)
   {
      NodeList rootList = doc.getElementsByTagName("server");
      Node root = rootList.item(0);
      root.appendChild(createApplication(doc, deployName, archiveName, type));
   }

   private void removeApplication(Document doc)
   {
      Node server = doc.getElementsByTagName("server").item(0);
      NodeList serverlist = server.getChildNodes();
      for (int i=0; serverlist.getLength() > i; i++) {
         Node node = serverlist.item(i);
         if (node.getNodeName().equals("application")) {
            node.getParentNode().removeChild(node);
         }
      }
   }

   private void waitForApplicationTargetState(String applicationName, boolean targetState, int timeout) throws DeploymentException {
      if (log.isLoggable(Level.FINER)) {
         log.entering(className, "waitForMBeanTargetState");
      }

      ObjectName appMBean = null;
      ObjectName listAllApps = null;
      try {
         appMBean = new ObjectName("WebSphere:service=com.ibm.websphere.application.ApplicationMBean,name=" + applicationName);
         listAllApps = new ObjectName("WebSphere:service=com.ibm.websphere.application.ApplicationMBean,name=*");
      } catch (MalformedObjectNameException e) {
         throw new DeploymentException("The generated object name is wrong. The applicationName used was '" + applicationName + "'", e);
      } catch (NullPointerException e) {
         // This should never happen given that the name parameter to the
         // ObjectName constructor above can never be null
         throw new DeploymentException("This should never happen", e);
      }
      
      // Loop until the application MBean has reached the target state or until the timeout
      try {
         int timeleft = timeout * 1000;
         while(mbsc.isRegistered(appMBean) != targetState) {
            Thread.sleep(100);
            if (timeleft <= 0) {
               Set<ObjectInstance> allApps = mbsc.queryMBeans(/*listAllApps*/ null, null);
               log.fine("Size of results: " + allApps.size());
               for (ObjectInstance app : allApps) {
                  log.fine(app.getObjectName().toString());
               }
               throw new DeploymentException("Timeout while waiting for ApplicationMBean to reach targetState");
            }
            timeleft -= 100;
         }
         
         // If the target state is true (true==STARTED)
         // then loop until the deployed application is in started state or until the timeout
         if (targetState == true) {
            String applicationState = null;
            while(applicationState == null || !applicationState.contentEquals("STARTED")) {
               Thread.sleep(100);
               applicationState = (String)mbsc.getAttribute(appMBean, "State");
               if (timeleft <= 0)
                  throw new DeploymentException("Timeout while waiting for ApplicationState to reach STARTED");
               timeleft -= 100;
            }
         }
      } catch (Exception e) {
         throw new DeploymentException("Exception while checking application state.", e);
      }
      
      if (log.isLoggable(Level.FINER)) {
         log.exiting(className, "waitForMBeanTargetState");
      }
   }

   public void stop() throws LifecycleException
   {
      if (log.isLoggable(Level.FINER)) {
         log.entering(className, "stop");
      }

      try {
         jmxConnector.close();
      } catch (IOException e) {
         throw new LifecycleException("Communication with the MBean Server failed.", e);
      }
      
      if (shutdownThread != null) {
         Runtime.getRuntime().removeShutdownHook(shutdownThread);
         shutdownThread = null;
      }
      try {
         if (wlpProcess != null) {
            wlpProcess.destroy();
            wlpProcess.waitFor();
            wlpProcess = null;
         }
      } catch (Exception e) {
         throw new LifecycleException("Could not stop container", e);
      }
      
      if (log.isLoggable(Level.FINER)) {
         log.exiting(className, "stop");
      }
   }

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
   public Class<WLPManagedContainerConfiguration> getConfigurationClass() {
      return WLPManagedContainerConfiguration.class;
   }

   public void deploy(Descriptor descriptor) throws DeploymentException {
      // TODO Auto-generated method stub
      
   }

   public void undeploy(Descriptor descriptor) throws DeploymentException {
      // TODO Auto-generated method stub
      
   }
   
   /**
    * Runnable that consumes the output of the process. If nothing consumes the output the process will hang on some platforms
    * Implementation from wildfly's ManagedDeployableContainer.java
    *
    * @author Stuart Douglas
    */
   private class ConsoleConsumer implements Runnable {

       @Override
       public void run() {
           final InputStream stream = wlpProcess.getInputStream();
           final boolean writeOutput = containerConfiguration.isOutputToConsole();

           try {
               byte[] buf = new byte[32];
               int num;
               // Do not try reading a line cos it considers '\r' end of line
               while ((num = stream.read(buf)) != -1) {
                   if (writeOutput)
                       System.out.write(buf, 0, num);
               }
           } catch (IOException e) {
           }
       }

   }
}
