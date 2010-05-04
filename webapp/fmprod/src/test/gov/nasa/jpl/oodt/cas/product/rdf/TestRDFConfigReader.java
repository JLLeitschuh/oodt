//Copyright (c) 2009, California Institute of Technology.
//ALL RIGHTS RESERVED. U.S. Government sponsorship acknowledged.
//
//$Id$

package gov.nasa.jpl.oodt.cas.product.rdf;

//JDK imports
import java.io.ByteArrayInputStream;
import org.w3c.dom.Document;

//OODT imports
import gov.nasa.jpl.oodt.cas.commons.xml.XMLUtils;

//JUnit imports
import junit.framework.TestCase;

/**
 * 
 * Exercises the {@link RDFConfigReader}.
 * 
 * @author mattmann
 * @version $Revision$
 * 
 */
public class TestRDFConfigReader extends TestCase {

  public void testReadNamespaces() {
    String testDocString = "<?xml version=\"1.0\"?><outer><namespaces><ns name=\"foo\" value=\"bar\"/></namespaces></outer>\n";
    Document testDoc = XMLUtils.getDocumentRoot(new ByteArrayInputStream(
        testDocString.getBytes()));
    RDFConfig conf = new RDFConfig();
    RDFConfigReader.readNamespaces(testDoc.getDocumentElement(), conf);
    assertNotNull(conf);
    assertNotNull(conf.getNsMap());
    assertNotNull(conf.getNsMap().keySet());
    assertEquals(1, conf.getNsMap().keySet().size());
    assertTrue(conf.getNsMap().containsKey("foo"));
    assertEquals("bar", conf.getNsMap().get("foo"));
  }

  public void testReadRewriteRules() {
    String testDocString = "<?xml version=\"1.0\"?><outer><rewrite><key from=\"SiteId\" to=\"site\"/></rewrite></outer>\n";
    Document testDoc = XMLUtils.getDocumentRoot(new ByteArrayInputStream(
        testDocString.getBytes()));
    RDFConfig conf = new RDFConfig();
    RDFConfigReader.readRewriteRules(testDoc.getDocumentElement(), conf);
    assertNotNull(conf);
    assertNotNull(conf.getRewriteMap());
    assertNotNull(conf.getRewriteMap().keySet());
    assertEquals(1, conf.getRewriteMap().keySet().size());
    assertTrue(conf.getRewriteMap().containsKey("SiteId"));
    assertEquals("site", conf.getRewriteMap().get("SiteId"));
  }

  public void testReadResourceLinks() {
    String testDocString = "<?xml version=\"1.0\"?><outer><resourcelinks><key name=\"SiteId\" link=\"http://edrn.nci.nih.gov/data/sites/\"/></resourcelinks></outer>\n";
    Document testDoc = XMLUtils.getDocumentRoot(new ByteArrayInputStream(
        testDocString.getBytes()));
    RDFConfig conf = new RDFConfig();
    RDFConfigReader.readResourceLinks(testDoc.getDocumentElement(), conf);
    assertNotNull(conf);
    assertNotNull(conf.getResLinkMap());
    assertNotNull(conf.getResLinkMap().keySet());
    assertEquals(1, conf.getResLinkMap().keySet().size());
    assertTrue(conf.getResLinkMap().containsKey("SiteId"));
    assertEquals("http://edrn.nci.nih.gov/data/sites/", conf.getResLinkMap()
        .get("SiteId"));
  }

  public void testReadKeyNsMap() {
    String testDocString = "<?xml version=\"1.0\"?><outer><keynsmap default=\"cas\"><key name=\"SiteId\" ns=\"x\"/></keynsmap></outer>\n";
    Document testDoc = XMLUtils.getDocumentRoot(new ByteArrayInputStream(
        testDocString.getBytes()));
    RDFConfig conf = new RDFConfig();
    RDFConfigReader.readKeyNsMap(testDoc.getDocumentElement(), conf);
    assertNotNull(conf);
    assertNotNull(conf.getKeyNsMap());
    assertNotNull(conf.getDefaultKeyNs());
    assertEquals("cas", conf.getDefaultKeyNs());
    assertNotNull(conf.getKeyNsMap().keySet());
    assertEquals(1, conf.getKeyNsMap().keySet().size());
    assertTrue(conf.getKeyNsMap().containsKey("SiteId"));
    assertEquals("x", conf.getKeyNsMap().get("SiteId"));
  }

  public void testReadTypeNsMap() {
    String testDocString = "<?xml version=\"1.0\"?><outer><typesnsmap default=\"cas\"><type name=\"GenericFile\" ns=\"cas\"/></typesnsmap></outer>\n";
    Document testDoc = XMLUtils.getDocumentRoot(new ByteArrayInputStream(
        testDocString.getBytes()));
    RDFConfig conf = new RDFConfig();
    RDFConfigReader.readTypesNsMap(testDoc.getDocumentElement(), conf);
    assertNotNull(conf);
    assertNotNull(conf.getTypesNsMap());
    assertNotNull(conf.getDefaultTypeNs());
    assertEquals("cas", conf.getDefaultTypeNs());
    assertNotNull(conf.getTypesNsMap().keySet());
    assertEquals(1, conf.getTypesNsMap().keySet().size());
    assertTrue(conf.getTypesNsMap().containsKey("GenericFile"));
    assertEquals("cas", conf.getTypesNsMap().get("GenericFile"));
  }
}
