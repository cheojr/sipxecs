/*
 *
 *
 * Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 *
 * $
 */
package org.sipfoundry.sipxconfig.dialplan.config;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.createStrictMock;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.sipfoundry.sipxconfig.test.XmlUnitHelper.assertElementInNamespace;
import static org.sipfoundry.sipxconfig.test.XmlUnitHelper.setNamespaceAware;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.custommonkey.xmlunit.XMLTestCase;
import org.custommonkey.xmlunit.XMLUnit;
import org.dom4j.Document;
import org.sipfoundry.sipxconfig.address.Address;
import org.sipfoundry.sipxconfig.address.AddressManager;
import org.sipfoundry.sipxconfig.branch.Branch;
import org.sipfoundry.sipxconfig.commserver.Location;
import org.sipfoundry.sipxconfig.dialplan.CallDigits;
import org.sipfoundry.sipxconfig.dialplan.CallPattern;
import org.sipfoundry.sipxconfig.dialplan.CallTag;
import org.sipfoundry.sipxconfig.dialplan.CustomDialingRule;
import org.sipfoundry.sipxconfig.dialplan.DialPattern;
import org.sipfoundry.sipxconfig.dialplan.EmergencyRule;
import org.sipfoundry.sipxconfig.dialplan.IDialingRule;
import org.sipfoundry.sipxconfig.gateway.Gateway;
import org.sipfoundry.sipxconfig.paging.PagingContext;
import org.sipfoundry.sipxconfig.parkorbit.ParkOrbitContext;
import org.sipfoundry.sipxconfig.rls.Rls;
import org.sipfoundry.sipxconfig.test.TestHelper;

public class FallbackRulesTest extends XMLTestCase {
    private FallbackRules m_out;

    public FallbackRulesTest() {
        setNamespaceAware(false);
        XMLUnit.setIgnoreWhitespace(true);
    }

    @Override
    public void setUp() {
        m_out = new FallbackRules();
        m_out.setDomainName("example.org");
        Location l = TestHelper.createDefaultLocation();
        AddressManager addressManager = createMock(AddressManager.class);
        addressManager.getSingleAddress(Rls.TCP_SIP, l);
        expectLastCall().andReturn(new Address(Rls.TCP_SIP, "rls.example.org", 100));
        addressManager.getSingleAddress(ParkOrbitContext.SIP_TCP_PORT, l);
        expectLastCall().andReturn(new Address(ParkOrbitContext.SIP_TCP_PORT, "park.example.org", 101));        
        addressManager.getSingleAddress(PagingContext.SIP_TCP, l);
        expectLastCall().andReturn(new Address(PagingContext.SIP_TCP, "page.example.org", 102));        
        replay(addressManager);
        m_out.setAddressManager(addressManager);
    }

    public void testGenerateRuleWithGateways() throws Exception {
        FullTransform t1 = new FullTransform();
        t1.setUser("333");
        t1.setHost("10.1.1.14");
        t1.setFieldParams("Q=0.97");
        t1.addHeaderParams("expires=7200");

        IDialingRule rule = createStrictMock(IDialingRule.class);
        rule.isInternal();
        expectLastCall().andReturn(false);
        rule.getHostPatterns();
        expectLastCall().andReturn(ArrayUtils.EMPTY_STRING_ARRAY);
        rule.getName();
        expectLastCall().andReturn("my test name");
        rule.getDescription();
        expectLastCall().andReturn("my test description");
        rule.getCallTag();
        expectLastCall().andReturn(CallTag.CUST).anyTimes();
        rule.getPatterns();
        expectLastCall().andReturn(array("x."));
        rule.getSiteTransforms();
        Map<String, List< ? extends Transform>> siteMap = new HashMap<String, List< ? extends Transform>>();
        siteMap.put(StringUtils.EMPTY, Arrays.asList(t1));
        expectLastCall().andReturn(siteMap);

        replay(rule);

        IDialingRule emergencyRule = createStrictMock(IDialingRule.class);
        emergencyRule.isInternal();
        expectLastCall().andReturn(false);
        emergencyRule.getHostPatterns();
        expectLastCall().andReturn(ArrayUtils.EMPTY_STRING_ARRAY);
        emergencyRule.getName();
        expectLastCall().andReturn("emergency name");
        emergencyRule.getDescription();
        expectLastCall().andReturn("emergency description");
        emergencyRule.getCallTag();
        expectLastCall().andReturn(CallTag.EMERG).anyTimes();
        emergencyRule.getPatterns();
        expectLastCall().andReturn(array("sos", "911", "9911"));
        emergencyRule.getSiteTransforms();
        Map<String, List< ? extends Transform>> emergencySiteMap = new HashMap<String, List< ? extends Transform>>();
        emergencySiteMap.put(StringUtils.EMPTY, Arrays.asList(t1));
        expectLastCall().andReturn(emergencySiteMap);

        replay(emergencyRule);

        m_out.begin();
        m_out.generate(rule);
        m_out.generate(emergencyRule);
        m_out.end();
        m_out.setLocation(TestHelper.createDefaultLocation());

        Document document = m_out.getDocument();

        assertElementInNamespace(document.getRootElement(),
                "http://www.sipfoundry.org/sipX/schema/xml/fallback-00-00");

        String domDoc = TestHelper.asString(document);
        InputStream referenceXmlStream = getClass().getResourceAsStream("fallbackrules.test.xml");
        assertEquals(IOUtils.toString(referenceXmlStream), domDoc);

        verify(rule);
    }

    public void testGenerateRuleWithGatewaysAndSite() throws Exception {
        Transform t1 = createTransform("444", "montreal.example.org", "q=0.95", "expires=7200");
        Transform t2 = createTransform("9444", "lisbon.example.org", "q=0.95", "expires=7200");

        Map<String, List<Transform>> siteTr = new LinkedHashMap<String, List<Transform>>();
        siteTr.put("Montreal", Arrays.asList(t1));
        siteTr.put("Lisbon", Arrays.asList(t2));

        IDialingRule rule = createStrictMock(IDialingRule.class);
        rule.isInternal();
        expectLastCall().andReturn(false);
        rule.getHostPatterns();
        expectLastCall().andReturn(ArrayUtils.EMPTY_STRING_ARRAY);
        rule.getName();
        expectLastCall().andReturn("my test name");
        rule.getDescription();
        expectLastCall().andReturn("my test description");
        rule.getCallTag();
        expectLastCall().andReturn(CallTag.CUST).anyTimes();
        rule.getPatterns();
        expectLastCall().andReturn(array("x."));
        rule.getSiteTransforms();
        expectLastCall().andReturn(siteTr);

        replay(rule);

        IDialingRule emergencyRule = createStrictMock(IDialingRule.class);
        emergencyRule.isInternal();
        expectLastCall().andReturn(false);
        emergencyRule.getHostPatterns();
        expectLastCall().andReturn(ArrayUtils.EMPTY_STRING_ARRAY);
        emergencyRule.getName();
        expectLastCall().andReturn("emergency name");
        emergencyRule.getDescription();
        expectLastCall().andReturn("emergency description");
        emergencyRule.getCallTag();
        expectLastCall().andReturn(CallTag.EMERG).anyTimes();
        emergencyRule.getPatterns();
        expectLastCall().andReturn(array("sos", "911", "9911"));
        emergencyRule.getSiteTransforms();
        expectLastCall().andReturn(siteTr);

        replay(emergencyRule);

        m_out.begin();
        m_out.generate(rule);
        m_out.generate(emergencyRule);
        m_out.end();
        m_out.setLocation(TestHelper.createDefaultLocation());

        Document document = m_out.getDocument();
        assertElementInNamespace(document.getRootElement(),
                "http://www.sipfoundry.org/sipX/schema/xml/fallback-00-00");

        String domDoc = TestHelper.asString(document);

        InputStream referenceXmlStream = getClass().getResourceAsStream("fallbackrules-sites.test.xml");
        assertEquals(IOUtils.toString(referenceXmlStream), domDoc);

        verify(rule);
    }

    private Transform createTransform(String user, String host, String q, String expires) {
        FullTransform t1 = new FullTransform();
        t1.setUser(user);
        t1.setHost(host);
        t1.setFieldParams(q);
        t1.setHeaderParams(expires);
        return t1;
    }

    public void testGenerateRuleWithGatewaysAndShared() throws Exception {
        Branch montrealSite = new Branch();
        montrealSite.setName("Montreal");

        Branch lisbonSite = new Branch();
        lisbonSite.setName("Lisbon");

        Gateway montreal = new Gateway();
        montreal.setUniqueId(100);
        montreal.setAddress("montreal.example.org");
        montreal.setBranch(montrealSite);
        montreal.setShared(false);

        Gateway lisbon = new Gateway();
        lisbon.setUniqueId(200);
        lisbon.setAddress("lisbon.example.org");
        lisbon.setBranch(lisbonSite);
        lisbon.setPrefix("9");

        Gateway shared = new Gateway();
        shared.setUniqueId(300);
        shared.setAddress("example.org");
        shared.setPrefix("8");
        shared.setShared(false);

        CustomDialingRule rule = new CustomDialingRule();
        rule.setName("my test name");
        rule.setDescription("my test description");
        rule.addGateway(shared);
        rule.addGateway(montreal);
        rule.addGateway(lisbon);
        rule.setCallPattern(new CallPattern("444", CallDigits.NO_DIGITS));
        rule.setDialPatterns(Arrays.asList(new DialPattern("x", DialPattern.VARIABLE_DIGITS)));

        EmergencyRule emergencyRule = new EmergencyRule();
        emergencyRule.setName("emergency name");
        emergencyRule.setDescription("emergency description");
        emergencyRule.setEmergencyNumber("911");
        emergencyRule.setOptionalPrefix("9");
        emergencyRule.addGateway(shared);
        emergencyRule.addGateway(montreal);
        emergencyRule.addGateway(lisbon);

        m_out.begin();
        m_out.generate(rule);
        m_out.generate(emergencyRule);
        m_out.end();
        m_out.setLocation(TestHelper.createDefaultLocation());

        Document document = m_out.getDocument();

        assertElementInNamespace(document.getRootElement(),
                "http://www.sipfoundry.org/sipX/schema/xml/fallback-00-00");

        String domDoc = TestHelper.asString(document);

        InputStream referenceXmlStream = getClass().getResourceAsStream("fallbackrules-shared-gateway.test.xml");
        assertEquals(IOUtils.toString(referenceXmlStream), domDoc);
    }

    public void testGenerateRuleWithoutGateways() throws Exception {
        IDialingRule rule = createMock(IDialingRule.class);
        rule.isInternal();
        expectLastCall().andReturn(true);
        replay(rule);

        m_out.begin();
        m_out.generate(rule);
        m_out.end();
        m_out.setLocation(TestHelper.createDefaultLocation());

        Document document = m_out.getDocument();

        assertElementInNamespace(document.getRootElement(),
                "http://www.sipfoundry.org/sipX/schema/xml/fallback-00-00");

        String domDoc = TestHelper.asString(document);

        InputStream referenceXmlStream = getClass().getResourceAsStream("fallbackrules-no-gateway.test.xml");
        assertEquals(IOUtils.toString(referenceXmlStream), domDoc);
        verify(rule);
    }

    private static <T> T[] array(T... items) {
        return items;
    }
}
