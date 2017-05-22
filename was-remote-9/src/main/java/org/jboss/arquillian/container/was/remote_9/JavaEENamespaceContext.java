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
package org.jboss.arquillian.container.was.remote_9;

import java.util.Iterator;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;

public class JavaEENamespaceContext implements NamespaceContext {

	public String getNamespaceURI(String prefix) {
        if (prefix == null) throw new NullPointerException("Null prefix");
        else if ("javaee".equals(prefix)) return "http://java.sun.com/xml/ns/javaee";
        else if ("j2ee".equals(prefix)) return "http://java.sun.com/xml/ns/j2ee"; //J2EE 1.4 XML Schemas for backwards compatibility
        else if ("xml".equals(prefix)) return XMLConstants.XML_NS_URI;
        return XMLConstants.NULL_NS_URI;
	}

	public String getPrefix(String uri) {
		// TODO Auto-generated method stub
		return null;
	}

	public Iterator getPrefixes(String uri) {
		// TODO Auto-generated method stub
		return null;
	}

}
