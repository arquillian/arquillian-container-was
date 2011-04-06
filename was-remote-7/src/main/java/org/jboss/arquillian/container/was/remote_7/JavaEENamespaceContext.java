package org.jboss.arquillian.container.was.remote_7;

import java.util.Iterator;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;

public class JavaEENamespaceContext implements NamespaceContext {

	public String getNamespaceURI(String prefix) {
        if (prefix == null) throw new NullPointerException("Null prefix");
        else if ("javaee".equals(prefix)) return "http://java.sun.com/xml/ns/javaee";
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
