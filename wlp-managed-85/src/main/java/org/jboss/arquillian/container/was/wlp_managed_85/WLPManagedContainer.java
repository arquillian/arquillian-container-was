/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.arquillian.container.was.wlp_managed_85;

import java.io.File;
import java.io.IOException;
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

import com.sun.tools.attach.AttachNotSupportedException;
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

   public void start() throws LifecycleException
   {
      if (log.isLoggable(Level.FINER)) {
         log.entering(className, "start");
      }

      // Find WebSphere Liberty Profile VMs by looking for ws-launch.jar and the name of the server
      String vmid = findVirtualMachineIdByName("ws-launch.jar " + containerConfiguration.getServerName());
      if (vmid == null)
         throw new LifecycleException("Unable to find virtual machine for serverName");
      
      VirtualMachine wlpvm = null;
      String serviceURL = null;
      
      try {
         wlpvm = VirtualMachine.attach(vmid);
         
         serviceURL = wlpvm.getAgentProperties().getProperty("com.sun.management.jmxremote.localConnectorAddress");
         if (serviceURL == null)
            throw new LifecycleException("Unable to retrieve connector address for localConnector");
      } catch (AttachNotSupportedException e) {
         throw new LifecycleException("Attaching to the localConnector's agent failed", e);
      } catch (IOException e) {
         throw new LifecycleException("Attaching to the localConnector's agent failed", e);
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
         int i = 0;
         while(mbsc.isRegistered(appMBean) != targetState) {
            Thread.sleep(100);
            if (i > (timeout * 10))
               throw new DeploymentException("Timeout while waiting for ApplicationMBean to reach targetState");
            i++;
         }
      } catch (IOException e) {
         throw new DeploymentException("Communication with the MBean Server failed.", e);
      } catch (InterruptedException e) {
         // Not planned to happen
         throw new DeploymentException("Thread has been interrupted", e);
      }
      
      // TODO: It might also be a good idea to check the applications status at this point, if this is a deployment.
      
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
}
