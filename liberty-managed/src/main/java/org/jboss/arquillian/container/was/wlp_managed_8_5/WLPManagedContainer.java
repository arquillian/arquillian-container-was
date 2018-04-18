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

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.jboss.arquillian.container.spi.client.container.DeployableContainer;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.spi.client.container.LifecycleException;
import org.jboss.arquillian.container.spi.client.protocol.ProtocolDescription;
import org.jboss.arquillian.container.spi.client.protocol.metadata.HTTPContext;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.arquillian.container.spi.client.protocol.metadata.Servlet;
import org.jboss.arquillian.container.test.api.Testable;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ArchivePath;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.descriptor.api.Descriptor;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Properties;
import javax.enterprise.inject.spi.DefinitionException;

/**
 * WLPManagedContainer
 *
 * @author <a href="mailto:gerhard.poul@gmail.com">Gerhard Poul</a>
 * @version $Revision: $
 */
public class WLPManagedContainer implements DeployableContainer<WLPManagedContainerConfiguration>
{

   // Environment variables that Liberty takes account of for messages.log location:
   // The precedence of the variables below can be thought of as building up from the bottom .
   // Strings that are "UPPER_CASE" are environment variables and Strings that are "camelCase"
   // can be set as properties. For properties, bootstrap.properties is read and used earlier
   // in the Liberty start process but once the server.xml <logging> element is read,
   // any equivalent value from there takes precedence.
   //
   private static final String DEFAULT_MESSAGES_LOG_NAME = "messages.log";
   private static final String MESSAGE_FILE_NAME = "messageFileName";
   private static final String MESSAGE_FILE_PROPERTY = "com.ibm.ws.logging.message.file.name";

   private static final String LOG_DIRECTORY = "logDirectory";
   private static final String LOG_DIRECTORY_PROPERTY = "com.ibm.ws.logging.log.directory";

   private static final String LOG_DIR = "LOG_DIR";
   private static final String WLP_OUTPUT_DIR = "WLP_OUTPUT_DIR";
   private static final String WLP_USER_DIR = "WLP_USER_DIR";
   
   private static final String ARQUILLIAN_SERVLET_NAME = "ArquillianServletRunner";


   private static final String className = WLPManagedContainer.class.getName();

   private static Logger log = Logger.getLogger(className);

   private static final String javaVmArgumentsDelimiter = " ";
   private static final String javaVmArgumentsIndicator = "-";

   private WLPManagedContainerConfiguration containerConfiguration;

   private JMXConnector jmxConnector;

   private MBeanServerConnection mbsc;

   private Process wlpProcess;

   private Thread shutdownThread;

   // Used in waitForApplicationTargetState
   // When targetState = true (registered), MATCHES_TARGET_STATE means the app is registered and FINISHED means the app is started.
   // When targetState = false (unregistered), MATCHES_TARGET_STATE and FINISHED both mean the application is unregistered.
   private enum AppStatus {
      INITIAL,
      MATCHES_TARGET_STATE,
      FINISHED
   }

   @Override
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
   @Override
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
            if (!javaVmArguments.equals("")) {
            	cmd.addAll(parseJvmArgs(javaVmArguments));
         	}
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

	private List<String> parseJvmArgs(String javaVmArguments) {
		List<String> parsedJavaVmArguments = new ArrayList<String>();
		String[] splitJavaVmArguments = javaVmArguments.split(javaVmArgumentsDelimiter);
		if (splitJavaVmArguments.length > 1) {
			for (String javaVmArgument : splitJavaVmArguments) {
				if (javaVmArgument.trim().length() > 0) {
					// remove precessing spaces
					if(javaVmArgument.startsWith(javaVmArgumentsIndicator)) {
						// vm argument without spaces
						parsedJavaVmArguments.add(javaVmArgument);
					} else {
						// space handling -> concat with the precessing argument
						String javaVmArgumentExtension = javaVmArgument;
						javaVmArgument = parsedJavaVmArguments.remove(parsedJavaVmArguments.size() - 1) + javaVmArgumentsDelimiter + javaVmArgumentExtension;
						parsedJavaVmArguments.add(javaVmArgument);
					}
				}
			}
		} else {
			parsedJavaVmArguments.add(javaVmArguments);
		}
		return parsedJavaVmArguments;
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

   @Override
   public ProtocolMetaData deploy(final Archive<?> archive) throws DeploymentException
   {
      if (log.isLoggable(Level.FINER)) {
         log.entering(className, "deploy");

         log.finer("Archive provided to deploy method: " + archive.toString(true));
      }

      waitForVerifyApps();

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

         // On deployment failure throw an Arquillian DeploymentException with a nested
         // 'cause' that can also be used in tests' ShouldThrowException(MyFrameworkException.class)
         try{
             // Wait until the application is deployed and available
             waitForApplicationTargetState(new String[] {deployName}, true, containerConfiguration.getAppDeployTimeout());

         }catch( DeploymentException dex) {
            // We will throw an exception below but lets log this one as we may create another with a different cause below
            log.warning( "Deployment exception seen: " + dex.getClass() + " " + dex.getMessage() );
            try {
               throwWrappedExceptionIfFoundInLog(deployName);
            } catch (IOException ioe) { // Don't catch DeploymentExceptions
              log.warning( ioe.getMessage() );
              ioe.printStackTrace(); //Throw the outer deployment exception caught above
            }
            throw dex;
         }

         // Return metadata on how to contact the deployed application
         ProtocolMetaData metaData = new ProtocolMetaData();
         HTTPContext httpContext = new HTTPContext("localhost", getHttpPort());
         List<WebModule> modules;
         if (archive instanceof EnterpriseArchive) {
             modules = getWebModules((EnterpriseArchive) archive);
         } else if (archive instanceof WebArchive) {
             WebModule m = new WebModule();
             m.name = deployName;
             m.contextRoot = deployName;
             m.archive = (WebArchive) archive;
             modules = Collections.singletonList(m);
         } else {
             modules = Collections.emptyList();
         }
         
         // register servlets
         boolean addedSomeServlets = false;
         for (WebModule module : modules) {
             List<String> servlets = getServletNames(deployName, module);
             for (String servlet : servlets) {
                 httpContext.add(new Servlet(servlet, module.contextRoot));
                 addedSomeServlets = true;
             }
         }
         
         if (!addedSomeServlets) {
             // Urk, we found no servlets at all probably because we don't have the J2EE management mbeans
             // Make a best guess at where servlets might be. Even if the servlet names are wrong, this at
             // least allows basic URL injection to work.
             if (modules.size() == 1) {
                 // If there's only one web module, add that
                 WebModule m = modules.get(0);
                 httpContext.add(new Servlet(ARQUILLIAN_SERVLET_NAME, m.contextRoot));
             } else {
                 httpContext.add(new Servlet(ARQUILLIAN_SERVLET_NAME, deployName));
             }
         }
         
         metaData.addContext(httpContext);

         if (log.isLoggable(Level.FINER)) {
            log.exiting(className, "deploy");
         }

         return metaData;
      } catch ( DeploymentException de ) {
         // Keep any more specific raised DeploymentExceptions
         throw de;
      }
      catch (Exception e) {
         // Wrap generic exceptions as DeploymentExceptions
         throw new DeploymentException("Exception while deploying application.", e);
      }
   }

   private void waitForVerifyApps() throws DeploymentException {
      String verifyApps = containerConfiguration.getVerifyApps();

      if(verifyApps != null && verifyApps.length() > 0) {
         String[] verifyAppArray = verifyApps.split(",");
         Set<String> verifyAppSet = new HashSet<String>();

         // Trim the whitespace off each app name
         for (int i = 0; i < verifyAppArray.length; i++) {
            String appToVerify = verifyAppArray[i];
            appToVerify = appToVerify.trim();
            if(appToVerify.length() > 0) {
               verifyAppSet.add(appToVerify);
            }
         }

         int totalTimeout = containerConfiguration.getVerifyAppDeployTimeout() * verifyAppSet.size();

         waitForApplicationTargetState(verifyAppSet.toArray(new String[verifyAppSet.size()]), true, totalTimeout);
      }
   }

   private List<WebModule> getWebModules(final EnterpriseArchive ear) throws DeploymentException {
       List<WebModule> modules = new ArrayList<WebModule>();
       
       for (ArchivePath path : ear.getContent().keySet()) {
           if (path.get().endsWith("war")) {
               WebModule module = new WebModule();
               module.archive = ear.getAsType(WebArchive.class, path);
               module.name = module.archive.getName().replaceFirst("\\.war$", "");
               module.contextRoot = getContextRoot(ear, module.archive);
               modules.add(module);
           }
       }
       return modules;
   }
   
   /**
    * Returns the short names of all servlets deployed in the module
    * <p>
    * Attempts to use J2EE management MBeans, falls back to just returning ArquillianServletRunner for testable archives and nothing otherwise.
    */
   private List<String> getServletNames(String appDeployName, WebModule webModule) throws DeploymentException {
       try {
           // If Java EE Management MBeans are present, query them for deployed servlets. This requires j2eeManagement-1.1 feature
           Set<ObjectInstance> servletMbeans = mbsc.queryMBeans(new ObjectName("WebSphere:*,J2EEApplication=" + appDeployName + ",j2eeType=Servlet,WebModule="+webModule.name), null);
           List<String> servletNames = new ArrayList<String>();
           
           for (ObjectInstance servletMbean : servletMbeans) {
               String name = servletMbean.getObjectName().getKeyProperty("name");
               
               // Websphere uses the fully qualified servlet class as the servlet name, but arquillian just wants the simple name
               if (name.contains(".")) {
                   name = name.substring(name.lastIndexOf(".") + 1);
               }
               
               servletNames.add(name);
           }
           
           // J2EE Management MBeans aren't always available, so if we didn't find any servlets and this is a testable archive
           // it ought to contain the arquillian test servlet, which is all that most tests need to work
           if (servletNames.isEmpty() && Testable.isArchiveToTest(webModule.archive)) {
               servletNames.add(ARQUILLIAN_SERVLET_NAME);
           }
           return servletNames;
       } catch (Exception e) {
           throw new DeploymentException("Error trying to retrieve servlet names", e);
       }
   }

   private String getContextRoot(EnterpriseArchive ear, WebArchive war) throws DeploymentException {
	   org.jboss.shrinkwrap.api.Node applicationXmlNode = ear.get("META-INF/application.xml");
	   if(applicationXmlNode != null && applicationXmlNode.getAsset() != null) {
		   InputStream input = null;
		   try {
			   input = ear.get("META-INF/application.xml").getAsset().openStream();
			   Document applicationXml = readXML(input);
			   XPath xPath = XPathFactory.newInstance().newXPath();
			   XPathExpression ctxRootSelector = xPath.compile("//module/web[web-uri/text()='"+ war.getName() +"']/context-root");
			   String ctxRoot = ctxRootSelector.evaluate(applicationXml);
			   if(ctxRoot != null && ctxRoot.trim().length() > 0) {
				   return ctxRoot;
			   }
		   } catch (Exception e) {
			   throw new DeploymentException("Unable to retrieve context-root from application.xml");
		   } finally {
			   closeQuietly(input);
		   }
	   }
	   return createDeploymentName(war.getName());
	}

	private static void closeQuietly(Closeable closable) {
		try {
			if (closable != null)
				closable.close();
		} catch (IOException e) {
			log.log(Level.WARNING, "Exception while closing Closeable", e);
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

   @Override
   public void undeploy(final Archive<?> archive) throws DeploymentException
   {
      if (log.isLoggable(Level.FINER)) {
         log.entering(className, "undeploy");
      }

      String archiveName = archive.getName();
      String deployName = createDeploymentName(archiveName);
      String deployDir = null; // will become either app or dropin dir

      try {
         // If deploy type is xml, then remove the application from the xml file, which causes undeploy
         if (containerConfiguration.isDeployTypeXML()) {
            // Read the server.xml file into Memory
            Document document = readServerXML();

            // Remove the archive from the server.xml file
            removeApplication(document, deployName);

            // Update server.xml on file system
            writeServerXML(document);

            // Wait until the application is undeployed
            waitForApplicationTargetState(new String[] {deployName}, false, containerConfiguration.getAppUndeployTimeout());
         }

         // Now we can proceed and delete the archive for either deploy type
         if (containerConfiguration.isDeployTypeXML()) {
            deployDir = getAppDirectory();
         } else {
            deployDir = getDropInDirectory();
         }

         // Remove the deployed archive
         File exportedArchiveLocation = new File(deployDir, archiveName);
         if (!containerConfiguration.isFailSafeUndeployment()) {
            try {
               if (!Files.deleteIfExists(exportedArchiveLocation.toPath())) {
                  throw new DeploymentException("Archive already deleted from deployment directory");
               }
            } catch (IOException e) {
               throw new DeploymentException("Unable to delete archive from deployment directory", e);
            }
         } else {
            try {
               Files.deleteIfExists(exportedArchiveLocation.toPath());
            } catch (IOException e) {
               log.log(Level.WARNING, "Unable to delete archive from deployment directory -> failsafe -> file marked for delete on exit", e);
               exportedArchiveLocation.deleteOnExit();
            }
         }

         // If it was the archive deletion that caused the undeploy we wait for the
         // correct state
         if (!containerConfiguration.isDeployTypeXML()) {
            // Wait until the application is undeployed
            waitForApplicationTargetState(new String[] {deployName}, false, containerConfiguration.getAppUndeployTimeout());
         }

      } catch (Exception e) {
         throw new DeploymentException("Exception while undeploying application.", e);
      }

      if (log.isLoggable(Level.FINER)) {
         log.exiting(className, "undeploy");
      }
   }

   private String getDropInDirectory() throws IOException {
      String dropInDir = getServerConfigDir() +
            "/dropins";
      if (log.isLoggable(Level.FINER))
         log.finer("dropInDir: " + dropInDir);
      return dropInDir;
   }

   private String getAppDirectory() throws IOException
   {
      String appDir = getServerConfigDir() + "/apps";
      if (log.isLoggable(Level.FINER))
         log.finer("appDir: " + appDir);
      return appDir;
   }

   private String getServerXML() throws IOException
   {
      String serverXML = getServerConfigDir() + "/server.xml";
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
      try {
    	      return readServerXML(getServerXML());
      } catch (IOException e) {
          throw new DeploymentException( "Can't read server.xml", e);
      }
   }

   private Document readServerXML(String serverXML) throws DeploymentException {
	   InputStream input = null;
	   try {
		   input = new FileInputStream(new File(serverXML));
		   return readXML(input);
	   } catch (Exception e) {
		   throw new DeploymentException("Exception while reading server.xml file.", e);
	   } finally {
	       closeQuietly(input);
	   }
	}

   private Document readXML(InputStream input) throws ParserConfigurationException, SAXException, IOException {
	   DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
	   DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
	   return documentBuilder.parse(input);
   }

   private void writeServerXML(Document doc) throws DeploymentException, IOException {
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

   private Element createApplication(Document doc, String deploymentName, String archiveName, String type) throws DeploymentException
   {
      // create new Application
      Element application = doc.createElement("application");
      application.setAttribute("id", deploymentName);
      application.setAttribute("location", archiveName);
      application.setAttribute("name", deploymentName);
      application.setAttribute("type", type);

      // create shared library
      if (containerConfiguration.getSharedLib() != null
          ||containerConfiguration.getApiTypeVisibility() != null) {

         Element classloader = doc.createElement("classloader");

         if (containerConfiguration.getSharedLib() != null) {
            classloader.setAttribute("commonLibraryRef", containerConfiguration.getSharedLib());
         }

         if (containerConfiguration.getApiTypeVisibility() != null) {
            classloader.setAttribute("apiTypeVisibility", containerConfiguration.getApiTypeVisibility());
         }
         application.appendChild(classloader);
      }

      if(containerConfiguration.getSecurityConfiguration() != null) {
         InputStream input = null;
         try {
            input = new FileInputStream(new File(containerConfiguration.getSecurityConfiguration()));
            Document securityConfiguration = readXML(input);
            application.appendChild(doc.adoptNode(securityConfiguration.getDocumentElement().cloneNode(true)));
         } catch (Exception e) {
            throw new DeploymentException("Exception while reading " + containerConfiguration.getSecurityConfiguration() + " file.", e);
         } finally {
            closeQuietly(input);
         }
      }

      return application;
   }

   private void addApplication(Document doc, String deployName, String archiveName, String type) throws DOMException, DeploymentException
   {
      NodeList rootList = doc.getElementsByTagName("server");
      Node root = rootList.item(0);
      root.appendChild(createApplication(doc, deployName, archiveName, type));
   }

   private void removeApplication(Document doc, String deployName)
   {
      Node server = doc.getElementsByTagName("server").item(0);
      NodeList serverlist = server.getChildNodes();
      for (int i=0; serverlist.getLength() > i; i++) {
         Node node = serverlist.item(i);
         if (node.getNodeName().equals("application") && node.getAttributes().getNamedItem("id").getNodeValue().equals(deployName)) {
            node.getParentNode().removeChild(node);
         }
      }
   }

   private void logAllApps() {
      try {
         log.info("Listing all apps...");
         Set<ObjectInstance> allApps = mbsc.queryMBeans(null, null);
         log.info("Size of results: " + allApps.size());
         for (ObjectInstance app : allApps) {
            log.info(app.getObjectName().toString());
         }
      } catch(IOException e) {
         log.warning("Could not print list of all apps. Exception thrown is: " + e.getMessage());
      }
   }

   private void waitForApplicationTargetState(String[] applicationNames, boolean targetState, int timeout) throws DeploymentException {
      if (log.isLoggable(Level.FINER)) {
         log.entering(className, "waitForApplicationTargetState");
      }

      Map<ObjectName, AppStatus> appMBeans = new HashMap<ObjectName, AppStatus>();

      for(String applicationName : applicationNames) {
    	 ObjectName appMBean = null;
         try {
            appMBean = new ObjectName("WebSphere:service=com.ibm.websphere.application.ApplicationMBean,name=" + applicationName);
         } catch (MalformedObjectNameException e) {
            throw new DeploymentException("The generated object name is wrong. The applicationName used was '" + applicationName + "'", e);
         } catch (NullPointerException e) {
            // This should never happen given that the name parameter to the
            // ObjectName constructor above can never be null
            throw new DeploymentException("This should never happen", e);
         }
         appMBeans.put(appMBean, AppStatus.INITIAL);
      }

      // Loop until the application MBean has reached the target state or until the timeout
      try {
         checkApplicationStatus(appMBeans, targetState, timeout);
      } catch (Exception e) {
         throw new DeploymentException("Exception while checking application state.", e);
      }

      if (log.isLoggable(Level.FINER)) {
         log.exiting(className, "waitForApplicationTargetState");
      }
   }

   private void checkApplicationStatus(Map<ObjectName, AppStatus> appMBeans, boolean targetState, int timeout) throws Exception {
      int timeleft = timeout * 1000;

      // Loop until all apps are ready. If timeleft is 0, fail the deployment
      do {
         for(Entry<ObjectName, AppStatus> entry : appMBeans.entrySet()) {
            ObjectName appMBean = entry.getKey();
            AppStatus status = entry.getValue();

            // First check if apps in the INITIAL state have reached the targetState. If so, update their AppStatus.
            if(status == AppStatus.INITIAL) {
               if(mbsc.isRegistered(appMBean) == targetState) {
                  status = AppStatus.MATCHES_TARGET_STATE;
               }
            }

            // Then check if apps in the targetState have reached the STARTED state, if the targetState == true.
            if(status == AppStatus.MATCHES_TARGET_STATE) {
               if(targetState == true) {
                  String applicationState = (String)mbsc.getAttribute(appMBean, "State");
                  if(applicationState.contentEquals("STARTED")) {
                     status = AppStatus.FINISHED;
                  }
               }
               else {
                  status = AppStatus.FINISHED;
               }
            }

            // Update the appMBeans dictionary
            appMBeans.put(appMBean, status);
         }

         if(allAppsReady(appMBeans)) {
            return;
         }

         Thread.sleep(100);

         timeleft -= 100;
      } while (timeleft > 0);

      // If we haven't returned in the while loop, not all apps were ready in the given timeout period.

      logAllApps();

      String appMessageStatus = "";
      for(Entry<ObjectName, AppStatus> entry : appMBeans.entrySet()) {
         // Timeout while waiting for ApplicationMBean to reach targetState
         String appName = entry.getKey().getCanonicalName();
         AppStatus status = entry.getValue();
         if(status == AppStatus.INITIAL) {
            appMessageStatus += "Timeout while waiting for \"" + appName + "\" ApplicationMBean to reach targetState.\n";
         }
         else if(status == AppStatus.MATCHES_TARGET_STATE) {
            appMessageStatus += "Timeout while waiting for \"" + appName + "\" ApplicationState to reach STARTED.\n";
         }
      }
      throw new DeploymentException(appMessageStatus);
   }

   private boolean allAppsReady(Map<ObjectName, AppStatus> appMBeans) {
      for(AppStatus status : appMBeans.values()) {
         if(status != AppStatus.FINISHED) {
            return false;
         }
      }
      return true;
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
    * Fetch a liberty ENV var. The sources have the following precedence: 1 -
    * server specific server.env 2 - system wide server.env 3 - shell environment
    *
    * @param key
    * @return ENV var value or null
    * @throws IOException
    */
   private String getLibertyEnvVar(String key) throws IOException {
      String value = null;
      Properties props = new Properties();
      InputStream fisServerEnv = null;
      InputStream fisSystemServerEnv = null;

      try {
         // Server specific
         if (!key.equals(WLP_USER_DIR)) { // WLP_USER_DIR can be specified only as a process environment variable
                                          // or ${wlp.install.dir}/etc/server.env file
            try {
               fisServerEnv = new FileInputStream(new File(getServerEnvFilename()));
               props.load(fisServerEnv);
               value = props.getProperty(key);
            } catch (FileNotFoundException fnfex) {
               // We ignore FileNotFound here
            }
         }
         // Liberty system wide not used for things that would collide across >1 server like LOG_DIR
         if (value == null && !key.equals(LOG_DIR)) {
            try {
               fisSystemServerEnv = new FileInputStream(new File(getSystemServerEnvFilename()));
               props.load(fisSystemServerEnv);
               value = props.getProperty(key);
            } catch (FileNotFoundException fnfex) {
               // We can safely ignore FileNotFound
            }
            // Process environment variables
            if (value == null && !key.equals(WLP_USER_DIR)) { // WLP_USER_DIR can be specified only in the
                                                              // ${wlp.install.dir}/etc/server.env file
               value = getEnv(key);
            }
         }

         log.fine("server.env: " + key + "=" + value);
         return value;

      } finally {
         closeQuietly(fisServerEnv);
         closeQuietly(fisSystemServerEnv);
      }
   }

   /**
    * Enable @ShouldThrowExceptions in tests by looking for messages that indicate
    * the cause of DeploymentExceptions
    *
    * @param applicationName
    * @throws IOException
    * @throws DeploymentException
    */
   private void throwWrappedExceptionIfFoundInLog(String applicationName) throws IOException, DeploymentException {
      BufferedReader br = null;
      String messagesFilePath = null;

      if (log.isLoggable(Level.FINER)) {
         log.entering(className, "throwWrappedExceptionIfFoundInLog");
      }


      try {
         messagesFilePath = getMessageFilePath();
         log.finest("Scanning message file " + messagesFilePath);


         br = new BufferedReader(new InputStreamReader(new FileInputStream(messagesFilePath)));
         String line;
         while ((line = br.readLine()) != null) {
            if (line.contains("CWWKZ0002") && line.contains("DefinitionException") && line.contains(applicationName)) {
               log.finest("DefinitionException found in line" + line + " of file " + messagesFilePath);
               DefinitionException cause = new javax.enterprise.inject.spi.DefinitionException(line);
               throw new DeploymentException(
                     "Failed to deploy " + applicationName + " on " + containerConfiguration.getServerName(), cause);
            } else {
               if (line.contains("DeploymentException") && line.contains(applicationName)) {
                  log.finest("DeploymentException found in line" + line + " of file " + messagesFilePath);
                  javax.enterprise.inject.spi.DeploymentException cause = new javax.enterprise.inject.spi.DeploymentException(
                        line);
                  throw new DeploymentException(
                        "Failed to deploy " + applicationName + " on " + containerConfiguration.getServerName(), cause);
               }
            }
         }
      } catch (IOException e) {
         log.warning("Exception while reading messages.log: " + messagesFilePath + ": " + e.toString());
         throw e;
      } catch (XPathExpressionException e) {
        log.warning(e.getMessage());
      } finally {
         closeQuietly(br);
      }

      if (log.isLoggable(Level.FINER)) {
         log.exiting(className, "throwWrappedExceptionIfFoundInLog");
      }

   }

   /**
    * Do System.getenv under a doPrivileged
    * @param name
    * @return
    */
   private String getEnv(final String name) {
      final String result = AccessController.doPrivileged(
         new PrivilegedAction<String>() {
            @Override
            public String run() {
               return System.getenv(name);
            }
         });
      return result;
   }

   /**
    * Get wlp/usr taking account of user preferences
    *
    * @return wlp.user.dir
    * @throws IOException
    */
   private String getWlpUsrDir() throws IOException {
      String usrDir = getLibertyEnvVar(WLP_USER_DIR);
      if (usrDir == null) {
         usrDir = containerConfiguration.getWlpHome() + "/usr/";
      }
      log.finer("wlp.usr.dir path: " + usrDir);
      return usrDir;
   }

   /**
    * Get server output dir taking account of user preferences
    *
    * @return server.output.dir
    * @throws IOException
    */
   private String getServerOutputDir() throws IOException {
      String serverOutputDir = null;
      String wlpOutputDir = getLibertyEnvVar(WLP_OUTPUT_DIR);
      if (wlpOutputDir == null) {
         serverOutputDir = getServerConfigDir(); // Output dir defaults to Config dir
      } else {
         serverOutputDir = wlpOutputDir + "/" + containerConfiguration.getServerName();
      }
      log.finer("server output dir path: " + serverOutputDir);
      return serverOutputDir;
   }

   /**
    * Get server config dir (where server.xml etc. usually are)
    * @return server.config.dir
    * @throws IOException
    */
   private String getServerConfigDir() throws IOException {
	   String serverConfigDir = getWlpUsrDir() + "servers/" + containerConfiguration.getServerName();
	   log.finer("server.config.dir path: " + serverConfigDir);
	   return serverConfigDir;
   }

   /**
    * Get logs directory taking account of user preferences
    *
    * @return server.output.dir/logs
    * @throws IOException
    */
   private String getLogsDirectory() throws IOException {
      String logDir = null;

      // 1 - from server.xml/server/logging/@logDirectory
      try {
         logDir = getServerXmlLoggingAttribute(LOG_DIRECTORY);
         log.finest("logDir getServerXmlLoggingAttribute: " + logDir);
      } catch (DeploymentException e) {
         // let logDir stay null
         log.warning(e.getMessage());
      } catch (XPathExpressionException e) {
         log.warning(e.getMessage());
      }

      // 2 - bootstrap.properties: com.ibm.ws.logging.log.directory
      if(logDir==null || logDir.length()==0) {
         logDir = getBootstrapProperty(LOG_DIRECTORY_PROPERTY);
         log.finest("logDir getBootstrapProperty(LOG_DIRECTORY_PROPERTY): " + logDir);
      }

      // 3 - Environment variable ${LOG_DIR}
      if (logDir == null || logDir.length()==0) {
         logDir = getLibertyEnvVar(LOG_DIR);
         log.finest("logDir getLibertyEnvVar: " + logDir);
      }

      // 4 - Default location e.g. "wlp/usr/<serverName>/logs"
      if (logDir == null || logDir.length()==0) {
            logDir = getServerOutputDir() + "/logs";
            log.finest("logDir getServerOutputDir: " + logDir);
      }

      log.finest("getLogsDirectory result: " + logDir);
      return logDir;
   }

   /**
    * Get a logging related attribute out of the server.xml
    *
    * @param attr
    * @return
    * @throws XPathExpressionException
    * @throws DeploymentException
    */
   private String getServerXmlLoggingAttribute(String attr) throws XPathExpressionException, DeploymentException{
      String loggingElementXpath = "/server/logging";
      String resultString = "";
      try {
         Document serverXml = readServerXML();
         XPathFactory xPathFactory = XPathFactory.newInstance();
         XPath xpath = xPathFactory.newXPath();
         Element loggingElement = null;
         loggingElement = (Element) xpath.evaluate(loggingElementXpath, serverXml, XPathConstants.NODE);
         if( loggingElement != null && loggingElement.hasAttribute(attr) ) {
            resultString = loggingElement.getAttribute(attr);
         }else {
            log.finest("logging element is null for " + loggingElementXpath + "/@" + attr );
         }
      } catch (XPathExpressionException e) {
         e.printStackTrace();
         log.finer("problem with expression: " + loggingElementXpath + " " + e.getMessage());
         throw e;
      } catch (DeploymentException e) {
         e.printStackTrace();
         log.finer("unreadable server.xml"  + e.getMessage());
         throw e;
      }

      log.finest("getServerXmlLoggingAttribute("  + attr + ")=" + resultString);
      return resultString;
   }

/**
   * Get location of the server.env file
   *
   * @return server.output.dir/logs
   * @throws IOException
   */
   private String getServerEnvFilename() throws IOException
   {
      String serverEnv = getServerConfigDir() + "/server.env";
      log.finer("server.env path: " + serverEnv);
      return serverEnv;
   }

   /**
    * Users can create system wide environment variables
    *
    * @return ${wlp.install.dir}/etc/server.env
    */
   private String getSystemServerEnvFilename()
   {
      String systemServerEnv = containerConfiguration.getWlpHome() + "/etc/server.env";
      log.finer("system wide server.env path: " + systemServerEnv);
      return systemServerEnv;
   }

   /**
    * Get the path of the messages.log file
    *
    * @return
    * @throws XPathExpressionException
    * @throws DeploymentException
    * @throws IOException
    */
   private String getMessageFilePath() throws XPathExpressionException, DeploymentException, IOException {
      String messagesFilePath;

      // We assume server.xml will have been read by the time we want to do any deploys so that takes precedence
      String msgFileName = getServerXmlLoggingAttribute(MESSAGE_FILE_NAME);
      if (msgFileName == null || msgFileName.length() == 0) {
         msgFileName = getBootstrapProperty(MESSAGE_FILE_PROPERTY);
         if (msgFileName == null || msgFileName.length() == 0) {
            msgFileName = DEFAULT_MESSAGES_LOG_NAME;
         }
      }

      messagesFilePath = getLogsDirectory() + "/" + msgFileName;
      log.finer("using message.log file path: " + messagesFilePath);

      return messagesFilePath;
   }

   /**
    * Get a bootstrap.properties property
    * @param name
    */
   private String getBootstrapProperty(String key) {
      Properties props = new Properties();
      FileInputStream fisBootstrapProperties = null;
      try {
         fisBootstrapProperties = new FileInputStream(getBootstrapPropertiesPath());
         props.load(fisBootstrapProperties);
      } catch (IOException ex) {
         log.finest(ex.getMessage());
      } finally {
         closeQuietly(fisBootstrapProperties);
      }
      String value=props.getProperty(key);
      log.finest("bootstrap.properties:" + key + "=" + value);
      return value;
   }

   /**
    * Get the FilePath of the bootstrap.properties file
    * @param messageFileProperty
    * @return
    */
   private String getBootstrapPropertiesPath() {
         String bootstrapProperties = null;
         try {
            bootstrapProperties = getServerConfigDir() + "/bootstrap.properties";
         } catch (IOException e) {
            log.warning(e.getMessage());
         }
         log.finest("bootstrap.properties: " + bootstrapProperties);
         return bootstrapProperties;
   }

   /**
    * Simple class to store the metadata for a web module
    */
   private static class WebModule {
       private String name;
       private String contextRoot;
       private WebArchive archive;
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
