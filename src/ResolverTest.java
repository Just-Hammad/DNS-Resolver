import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import java.net.InetAddress;

public class ResolverTest {

    private Resolver resolver;

    // ANSI escape codes for coloring in the console output
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_RED = "\u001B[31m";

    @Before
    public void setUp() throws Exception {
        resolver = new Resolver(); 
        // byte[] rootServer = new byte[] { -86, -9, -86, 2 };
        // byte[] cloudflarePublic = new byte[] { 1, 1, 1, 1 };
        byte[] rootServer = new byte[] { -58, 41, 0, 4 };
        resolver.setNameServer(InetAddress.getByAddress(rootServer), 53);

        // byte[] cloudflarePublic = new byte[] { 1, 1, 1, 1 };
        // resolver.setNameServer(InetAddress.getByAddress(cloudflarePublic), 53);
    }

    @Test
    public void testResolveARecord() throws Exception {
        System.out.println("\nStarting test: Resolve A Record");

        InetAddress address = resolver.iterativeResolveAddress("google.com");
        assertNotNull("A record should not be null", address);
        System.out.println(
                ANSI_GREEN + "Success: A record resolved for google.com: " + address.getHostAddress() + ANSI_RESET);
    }

    @Test
    public void testResolveNSRecord() throws Exception {
        System.out.println("\nStarting test: Resolve NS Record");

        String nsRecord = resolver.iterativeResolveName("google.com", 2); // NS record
        assertNotNull("NS record should not be null", nsRecord);
        System.out.println(ANSI_GREEN + "Success: NS record resolved for google.com: " + nsRecord + ANSI_RESET);
    }

    @Test
    public void testResolveMXRecord() throws Exception {
        System.out.println("\nStarting test: Resolve MX Record");

        String mxRecord = resolver.iterativeResolveName("google.com", 15); // MX record
        assertNotNull("MX record should not be null", mxRecord);
        System.out.println(ANSI_GREEN + "Success: MX record resolved for google.com: " + mxRecord + ANSI_RESET);
    }

    @Test
    public void testResolveTXTRecord() throws Exception {
        System.out.println("\nStarting test: Resolve TXT Record");

        String txtRecord = resolver.iterativeResolveText("city.ac.uk.");
        assertNotNull("TXT record should not be null", txtRecord);
        System.out.println(ANSI_GREEN + "Success: TXT record resolved for city.ac.uk.: " + txtRecord + ANSI_RESET);
    }

    @Test
    public void testResolveCNAMERecord() throws Exception {
        System.out.println("\nStarting test: Resolve CNAME Record");

        String cnameRecord = resolver.iterativeResolveName("moodle4.city.ac.uk", 5); // CNAME record
        assertNotNull("CNAME record should not be null", cnameRecord);
        System.out
                .println(ANSI_GREEN + "Success: CNAME record resolved for moodle4.city.ac.uk: " + cnameRecord
                        + ANSI_RESET);
    }

    @Test
    public void testUnresponsiveNameServers() throws Exception {
        System.out.println("\nStarting test: Handle Unresponsive Name Servers");
        byte[] unResponsiveServer = new byte[] { -64, 0, 2, 0 };

        resolver.setNameServer(InetAddress.getByAddress(unResponsiveServer), 24);
        try {
            resolver.iterativeResolveAddress("google.com");
            fail("Should have thrown an exception due to unresponsive name server");
        } catch (Exception e) {
            assertNotNull("Exception should not be null when name server is unresponsive", e.getMessage());
            System.out.println(ANSI_GREEN + "Success: Properly handled unresponsive name server." + ANSI_RESET);
        }
    }

    @Test
    public void testGlueRecords() throws Exception {
        System.out.println("\nStarting test: Resolve Glue Records");

        // This domain must be resolved using glue records for testing
        String domain = "ns1.google.com"; // Replace with your test domain
        String expectedGlueRecord = "216.239.32.10"; // Replace with the expected glue record IP

        InetAddress resolvedAddress = resolver.iterativeResolveAddress(domain);

        if (resolvedAddress == null) {
            System.err.println("Failed to resolve domain: " + domain);
            fail("Failed to resolve domain: " + domain);
        }

        String resolvedIp = resolvedAddress.getHostAddress();
        System.out.println("Resolved IP: " + resolvedIp);
        assertEquals("Glue record IP should match", expectedGlueRecord, resolvedIp);
        System.out.println(ANSI_GREEN + "Success: Glue record test passed." + ANSI_RESET);
    }
}