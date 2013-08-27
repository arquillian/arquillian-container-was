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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

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
         vmid = findVirtualMachineIdByName("ws-launch.jar " + containerConfiguration.getServerName());
         // If it has already been started, throw exception unless we explicitly allow connecting to a running server
         if (vmid != null) {
            if (!containerConfiguration.isAllowConnectingToRunningServer())
               throw new LifecycleException("Connecting to an already running server is not allowed");
            
            wlpvm = VirtualMachine.attach(vmid);
            
            serviceURL = wlpvm.getAgentProperties().getProperty("com.sun.management.jmxremote.localConnectorAddress");
            if (serviceURL == null)
               throw new LifecycleException("Unable to retrieve connector address for localConnector");
         } else {
            // Start the WebSphere Liberty Profile VM
            List<String> cmd = new ArrayList<String>();
            
            cmd.add(System.getProperty("java.home") + "/bin/java");
            cmd.add("-javaagent:lib/bootstrap-agent.jar");
            cmd.add("-jar");
            cmd.add("lib/ws-launch.jar");
            cmd.add(containerConfiguration.getServerName());
            
            log.finer("Starting server with command: " + cmd.toString());
            
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(new File(containerConfiguration.getWlpHome()));
            pb.redirectErrorStream();
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
            int startupTimeout = 30 * 1000;
            while (startupTimeout > 0 && serviceURL == null) {
               startupTimeout -= 500;
               Thread.sleep(500);
               
               if (vmid == null)
                  // Find WebSphere Liberty Profile VMs by looking for ws-launch.jar and the name of the server
                  vmid = findVirtualMachineIdByName("ws-launch.jar " + containerConfiguration.getServerName());
               
               if (wlpvm == null && vmid != null)
                  wlpvm = VirtualMachine.attach(vmid);
               
               if (serviceURL == null && wlpvm != null)
                  serviceURL = wlpvm.getAgentProperties().getProperty("com.sun.management.jmxremote.localConnectorAddress");
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

   private String findVirtualMachineIdByName(String name) {
      if (log.isLoggable(Level.FINER)) {
         log.entering(className, "findVirtualMachineIdByName");
      }

      List<VirtualMachineDescriptor> vmds = VirtualMachine.list();
      for (VirtualMachineDescriptor vmd : vmds) {
         if (log.isLoggable(Level.FINER)) {
            log.finer("VMD displayName: " + vmd.displayName());
            log.finer("VMD id: " + vmd.id());
         }
         if (vmd.displayName().contains(name)) {
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
      
      // Save the archive to disk so it can be loaded by the container.
      String dropInDir = getDropInDirectory();
      File exportedArchiveLocation = new File(dropInDir, archive.getName());
      archive.as(ZipExporter.class).exportTo(exportedArchiveLocation, true);
      
      // Wait until the application is deployed and available
      waitForApplicationTargetState(createDeploymentName(archive.getName()), true, 2);
      
      // Return metadata on how to contact the deployed application
      ProtocolMetaData metaData = new ProtocolMetaData();
      HTTPContext httpContext = new HTTPContext("localhost", containerConfiguration.getHttpPort());
      httpContext.add(new Servlet("ArquillianServletRunner", createDeploymentName(archive.getName())));
      metaData.addContext(httpContext);

      if (log.isLoggable(Level.FINER)) {
         log.exiting(className, "deploy");
      }
      
      return metaData;
   }

   public void undeploy(final Archive<?> archive) throws DeploymentException
   {
      if (log.isLoggable(Level.FINER)) {
         log.entering(className, "undeploy");
      }
      
      // Remove archive from the dropIn directory, which causes undeploy
      String dropInDir = getDropInDirectory();
      File exportedArchiveLocation = new File(dropInDir, archive.getName());
      if (!exportedArchiveLocation.delete())
         throw new DeploymentException("Unable to delete archive from dropIn directory");

      // Wait until the application is undeployed
      waitForApplicationTargetState(createDeploymentName(archive.getName()), false, 2);

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
   
   private String createDeploymentName(String archiveName) 
   {
      return archiveName.substring(0, archiveName.lastIndexOf("."));
   }
   
   private void waitForApplicationTargetState(String applicationName, boolean targetState, int timeout) throws DeploymentException {
      if (log.isLoggable(Level.FINER)) {
         log.entering(className, "waitForMBeanTargetState");
      }

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
      
      // Loop until the application MBean has reached the target state or until the timeout
      try {
         int timeleft = timeout * 1000;
         while(mbsc.isRegistered(appMBean) != targetState) {
            Thread.sleep(100);
            if (timeleft <= 0)
               throw new DeploymentException("Timeout while waiting for ApplicationMBean to reach targetState");
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
