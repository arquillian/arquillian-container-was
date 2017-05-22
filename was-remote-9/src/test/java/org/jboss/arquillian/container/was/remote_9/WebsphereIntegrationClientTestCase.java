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

import javax.ejb.EJB;

import junit.framework.Assert;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.was.remote_9.ejb.MyEjb;
import org.jboss.arquillian.container.was.remote_9.ejb.MyEjbLocal;
import org.jboss.arquillian.container.was.remote_9.ejb.MyEjbRemote;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * WebsphereIntegrationClientTestCase
 *
 * @author <a href="mailto:aslak@redhat.com">Aslak Knutsen</a>
 * @version $Revision: $
 */
@RunWith(Arquillian.class)
public class WebsphereIntegrationClientTestCase
{
   @Deployment
   public static EnterpriseArchive createDeployment() 
   {
      return ShrinkWrap.create(EnterpriseArchive.class, "IntegrationClientTestCase.ear")
                  .addAsModule(ShrinkWrap.create(JavaArchive.class, "test.jar")
                                    .addClass(MyEjb.class)
                                    .addClass(MyEjbLocal.class)
                                    .addClass(MyEjbRemote.class)
                                    .addClass(WebsphereIntegrationClientTestCase.class));
   }
   
   @EJB
   private MyEjbLocal localInstanceVariable;
   
   @EJB
   private MyEjbRemote remoteInstanceVariable;
   
   @Test
   public void shouldBeAbleToInjectLocalEJBAsInstanceVariable() throws Exception 
   {
      Assert.assertNotNull(
            "Verify that the local Bean has been injected",
            localInstanceVariable);
      
      Assert.assertEquals("aslak", localInstanceVariable.getName());
   }
   
   @Test
   public void shouldBeAbleToInjectRemoteEJBAsInstanceVariable() throws Exception
   {
      Assert.assertNotNull(
            "Verify that the remote Bean has been injected",
            remoteInstanceVariable);
      
      Assert.assertEquals("aslak", remoteInstanceVariable.getName());
   }

}
