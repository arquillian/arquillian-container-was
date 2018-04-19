/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010-2012, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.arquillian.container.was.wlp_managed_8_5.needsmanagementmbeans;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.descriptor.api.Descriptors;
import org.jboss.shrinkwrap.descriptor.api.application7.ApplicationDescriptor;
import org.junit.Test;
import org.junit.runner.RunWith;


@RunWith(Arquillian.class)
public class WLPInjectServletContextTest
{
    
    private static final String DEPLOYMENT1 = "app1";
    private static final String DEPLOYMENT2 = "app2";
    
    
    @Deployment(testable = false, name = DEPLOYMENT1)
    public static WebArchive app1() {
        return ShrinkWrap.create(WebArchive.class)
                .addClass(FooServlet.class);
    }
    
    @Deployment(testable = false, name = DEPLOYMENT2)
    public static EnterpriseArchive app2() {
        EnterpriseArchive ear = ShrinkWrap.create(EnterpriseArchive.class)
                .addAsModule(ShrinkWrap.create(WebArchive.class, "test1.war")
                             .addClass(BarServlet.class))
                .addAsModule(ShrinkWrap.create(WebArchive.class, "test2.war")
                             .addClass(BazServlet.class));
        
        ApplicationDescriptor appXml = Descriptors.create(ApplicationDescriptor.class)
                                                     .version(ApplicationDescriptor.VERSION)
                                                     .applicationName("testApp")
                                                     .createModule().getOrCreateWeb().contextRoot("/test1").webUri("test1.war").up().up()
                                                     .createModule().getOrCreateWeb().contextRoot("/test2").webUri("test2.war").up().up()
                                                     ;
        ear.setApplicationXML(new StringAsset(appXml.exportAsString()));
        
        return ear;
    }
    
    @ArquillianResource(FooServlet.class)
    @OperateOnDeployment(DEPLOYMENT1)
    private URL fooContextRoot;
    
    @ArquillianResource(BarServlet.class)
    @OperateOnDeployment(DEPLOYMENT2)
    private URL barContextRoot;
    
    @ArquillianResource(BazServlet.class)
    @OperateOnDeployment(DEPLOYMENT2)
    private URL bazContextRoot;
    
    @Test
    public void testFoo() throws Exception {
        URL url = new URL(fooContextRoot, "foo");
        String response = readAllAndClose(url.openStream());
        assertEquals("I am foo", response);
    }
    
    @Test
    public void testBar() throws Exception {
        URL url = new URL(barContextRoot, "bar");
        String response = readAllAndClose(url.openStream());
        assertEquals("I am bar", response);
    }
    
    @Test
    public void testBaz() throws Exception {
        URL url = new URL(bazContextRoot, "baz");
        String response = readAllAndClose(url.openStream());
        assertEquals("I am baz", response);
    }
    
    private String readAllAndClose(InputStream is) throws Exception 
    {
       ByteArrayOutputStream out = new ByteArrayOutputStream();
       try
       {
          int read;
          while( (read = is.read()) != -1)
          {
             out.write(read);
          }
       }
       finally 
       {
          try { is.close(); } catch (Exception e) { }
       }
       return out.toString();
    }

}
