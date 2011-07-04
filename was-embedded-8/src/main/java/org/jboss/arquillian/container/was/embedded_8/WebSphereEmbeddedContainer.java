/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.arquillian.container.was.embedded_8;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ejb.embeddable.EJBContainer;
import javax.naming.Context;

import org.jboss.arquillian.container.spi.client.container.DeployableContainer;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.spi.client.container.LifecycleException;
import org.jboss.arquillian.container.spi.client.protocol.ProtocolDescription;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.arquillian.container.spi.context.annotation.ContainerScoped;
import org.jboss.arquillian.core.api.InstanceProducer;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.descriptor.api.Descriptor;

/**
 * WebSphereEmbeddedContainer
 *
 * @author <a href="mailto:gerhard.poul@gmail.com">Gerhard Poul</a>
 * @version $Revision: $
 */
public class WebSphereEmbeddedContainer implements DeployableContainer<WebSphereEmbeddedContainerConfiguration>
{
   //-------------------------------------------------------------------------------------||
   // Instance Members -------------------------------------------------------------------||
   //-------------------------------------------------------------------------------------||
   
   private static final String className = WebSphereEmbeddedContainer.class.getName();
   
   private static Logger log = Logger.getLogger(className);
   
   private WebSphereEmbeddedContainerConfiguration containerConfiguration;
   
   private EJBContainer ec;
   
   /**
    * The JNDI Context for this container.
    */
   @Inject
   @ContainerScoped
   private InstanceProducer<Context> jndiContext;

   //-------------------------------------------------------------------------------------||
   // Required Implementations - DeployableContainer -------------------------------------||
   //-------------------------------------------------------------------------------------||

   public void setup(WebSphereEmbeddedContainerConfiguration configuration)
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
      
      // There should be nothing to do here.
      
      if (log.isLoggable(Level.FINER)) {
         log.exiting(className, "start");
      }
   }

   public ProtocolMetaData deploy(final Archive<?> archive) throws DeploymentException
   {
      if (log.isLoggable(Level.FINER)) {
         log.entering(className, "deploy");
         
         log.finer("Archive provided to deploy method: " + archive.toString(true));
      }
      
      // Save the archive to disk so it can be loaded by the container.
      // For JNDI lookups to work correctly the archive name must match the
      // one of the provided archive file.
      String tmpDir = System.getProperty("java.io.tmpdir");
      File exportedArchiveLocation = new File(tmpDir, archive.getName());
      archive.as(ZipExporter.class).exportTo(exportedArchiveLocation, true);
      
      // Create the properties object to pass to the embeddable container:
      Map<String,Object> props = new HashMap<String,Object>();
      
      // Set the embeddable container configuration file if it has been
      // provided in the arquillian configuration.
      if (containerConfiguration.getEmbeddedProperties() != null)
         props.put("com.ibm.websphere.embeddable.configFileName", containerConfiguration.getEmbeddedProperties());

      // Specify the EJB modules to start when creating the container:
      File[] ejbModules = new File[1];
      ejbModules[0] = exportedArchiveLocation;
      props.put(EJBContainer.MODULES, ejbModules);
      
      // Start the Embeddable Container
      ec = EJBContainer.createEJBContainer(props);
      
      // Set the JNDI Context
      jndiContext.set(ec.getContext());
      
      if (log.isLoggable(Level.FINER)) {
         log.exiting(className, "deploy");
      }
      
      return new ProtocolMetaData();
   }

   public void undeploy(final Archive<?> archive) throws DeploymentException
   {
      if (log.isLoggable(Level.FINER)) {
         log.entering(className, "undeploy");
      }
      
      // Close the Embeddable Container
      ec.close();
      
      if (log.isLoggable(Level.FINER)) {
         log.exiting(className, "undeploy");
      }
   }

   public void stop() throws LifecycleException
   {
      if (log.isLoggable(Level.FINER)) {
         log.entering(className, "stop");
      }

      // There should be nothing to do here.
      
      if (log.isLoggable(Level.FINER)) {
         log.exiting(className, "stop");
      }
   }

	public ProtocolDescription getDefaultProtocol() {
      if (log.isLoggable(Level.FINER)) {
         log.entering(className, "getDefaultProtocol");
      }
      
      if (log.isLoggable(Level.FINER)) {
         log.exiting(className, "getDefaultProtocol");
      }
      
		return new ProtocolDescription("Local");
	}

	@Override
   public Class<WebSphereEmbeddedContainerConfiguration> getConfigurationClass() {
      return WebSphereEmbeddedContainerConfiguration.class;
   }

   public void deploy(Descriptor descriptor) throws DeploymentException {
      // TODO Auto-generated method stub
      
   }

   public void undeploy(Descriptor descriptor) throws DeploymentException {
      // TODO Auto-generated method stub
      
   }
}
