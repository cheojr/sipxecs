/**
 * Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 */
package org.sipfoundry.preflight;

import java.io.*;
import java.net.*;
import java.util.*;

import org.xbill.DNS.*;

import static org.sipfoundry.preflight.ResultCode.*;

/**
 * [Enter descriptive text here]
 * <p>
 * 
 * @author Mardy Marshall
 */
public class HTTP {
    public ResultCode validate(int timeout, NetworkResources networkResources, JournalService journalService) {
        ResultCode results = NONE;
        InetAddress httpServerAddress = null;
        String testFile = new String("00D01EFFFFFE");
        String testFileContents = "";
        String[] verificationStrings = {
                "LIP-68XX configuration information",
                "[VOIP]", "outbound_proxy_server",
                "[PROVISION]",
                "decrypt_key"
        };

        if (networkResources.configServer == null) {
            journalService.println("No HTTP server provided, skipping test.");
            return CONFIG_SERVER_MISSING;
        }

        journalService.println("Starting HTTP server test.");

        // Try to retrieve A RECORD for HTTP server, checking each DNS server.
        SimpleResolver resolver = null;
        try {
            resolver = new SimpleResolver();
            resolver.setTimeout(timeout);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        for (InetAddress dnsServer : networkResources.domainNameServers) {
            journalService.println("Looking up HTTP server address via DNS server: " + dnsServer.getCanonicalHostName());
            String targetMessage = new String("  HTTP server address \"" + networkResources.configServer + "\"");
            resolver.setAddress(dnsServer);
            Lookup aLookup = null;
            try {
                aLookup = new Lookup(networkResources.configServer, Type.A);
            } catch (TextParseException e) {
                journalService.println("  is malformed.\n");
                journalService.println(targetMessage);
                return HTTP_URL_MALFORMED;
            }
            aLookup.setResolver(resolver);
            Record[] aRecords = aLookup.run();
            switch (aLookup.getResult()) {
                case Lookup.SUCCESSFUL:
                    if (aRecords != null) {
                        InetAddress targetAddress = ((ARecord) aRecords[0]).getAddress();
                        targetMessage += " resolves to: " + targetAddress.getHostAddress();
                        journalService.println(targetMessage);
                        if (httpServerAddress == null) {
                            httpServerAddress = targetAddress;
                        } else {
                            // Check that multiple lookups result in same
                            // address.
                            if (!httpServerAddress.equals(targetAddress)) {
                                journalService.println("  HTTP server address does not match prior lookup.");
                                results = MULTIPLE_CONFIG_TARGETS;
                            }
                        }
                    } else {
                        targetMessage += " could not be resolved.";
                        journalService.println(targetMessage);
                        results = HTTP_TARGET_UNRESOLVED;
                    }
                    break;
                case Lookup.UNRECOVERABLE:
                    targetMessage += " [Unrecoverable error]";
                    journalService.println(targetMessage);
                    results = HTTP_TARGET_UNRESOLVED;
                    break;
                case Lookup.TRY_AGAIN:
                    targetMessage += " [Lookup timeout]";
                    journalService.println(targetMessage);
                    results = HTTP_TARGET_UNRESOLVED;
                    break;
                case Lookup.HOST_NOT_FOUND:
                    targetMessage += " could not be resolved.";
                    journalService.println(targetMessage);
                    results = HTTP_TARGET_UNRESOLVED;
                    break;
                case Lookup.TYPE_NOT_FOUND:
                    targetMessage += " could not be resolved.";
                    journalService.println(targetMessage);
                    results = HTTP_TARGET_UNRESOLVED;
                    break;
            }
        }

        if ((httpServerAddress == null) || (results == MULTIPLE_CONFIG_TARGETS)) {
            journalService.println("Cannot recover from previous errors, aborting HTTP test.");
            return results;
        }

        journalService.println("Beginning HTTP GET request of test file: " + testFile);
        // Try to receive remote file via HTTP
        URL configUrl;
        try {
            configUrl = new URL("http://" + httpServerAddress.getHostAddress() + ":8090/phone/profile/docroot/" + testFile);
            HttpURLConnection http = (HttpURLConnection) configUrl.openConnection();
            http.setRequestMethod("GET");
            http.setConnectTimeout(timeout * 1000);
            http.setReadTimeout(timeout * 1000);
            int code = http.getResponseCode();
            String response = http.getResponseMessage();
            journalService.println("Received:");
            journalService.println("  HTTP/1.x " + code + " " + response);
            int contentLength = 0;
            // Dump out headers.
            for (int i = 1;; i++) {
                String header = http.getHeaderField(i);
                String key = http.getHeaderFieldKey(i);
                if ((header == null) || (key == null)) {
                    break;
                } else if (key.compareToIgnoreCase("Content-Length") == 0) {
                    contentLength = Integer.parseInt(header);
                }
                journalService.println("  " + key + ": " + header);
            }
            // If connection successful, read the file.
            if (code == 200) {
                InputStream input = new BufferedInputStream(http.getInputStream());
                Reader r = new InputStreamReader(input);
                int c;
                while ((c = r.read()) != -1) {
                    testFileContents += (char) c;
                }
                if (testFileContents.length() != contentLength) {
                    journalService.println("File received but invalid length.");
            		results = HTTP_CONTENTS_FAILED;
                }
            } else {
                journalService.println("HTTP GET request failed: " + code + " " + response);
            	return HTTP_GET_FAILED;
            }
        } catch (MalformedURLException e) {
            journalService.println(e.getMessage());
            return HTTP_URL_MALFORMED;
        } catch (IOException e) {
            journalService.println(e.getMessage());
            return HTTP_TARGET_UNREACHABLE;
        }

        boolean verified = true;
        for (String verificationString : verificationStrings) {
            if (!testFileContents.contains(verificationString)) {
                verified = false;
            }
        }
        if (verified) {
            journalService.println("File received successfully.");
        } else {
            journalService.println("File received but contents do not verify.");
            results = HTTP_CONTENTS_FAILED;
        }

        journalService.println("");
        return results;
    }

    public static final void main(String[] args) {
        class ConsoleJournalService implements JournalService {
            private boolean isEnabled = true;
            
            public void enable() {
                isEnabled = true;
            }
            
            public void disable() {
                isEnabled = false;
            }

            public void print(String message) {
                if (isEnabled) {
                    System.out.print(message);
                }
            }
            
            public void println(String message) {
                if (isEnabled) {
                    System.out.println(message);
                }
            }
        }

        HTTP test = new HTTP();
        NetworkResources networkResources = new NetworkResources();
        //networkResources.configServer = "sipx-install.pingtel.com";
        networkResources.configServer = "sipxchange.pingtel.com";
        networkResources.domainNameServers = new LinkedList<InetAddress>();
        try {
            networkResources.domainNameServers.add(InetAddress.getByName("10.1.1.71"));
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        test.validate(10, networkResources, new ConsoleJournalService());
    }

}
