import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.Random;

public class NameServerExtra {

    private NameServer nameServer;

    // ANSI escape codes for coloring
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_RED = "\u001B[31m";

    @Before
    public void setUp() throws Exception {
        nameServer = new NameServer();
        nameServer.setNameServer(InetAddress.getByName("8.8.8.8"), 53);
    }

    @Test
    public void testMultipleClients() throws Exception {
        System.out.println("\nStarting test: Multiple Clients");

        // Start the DNS server
        new Thread(() -> {
            try {
                nameServer.handleIncomingQueries(7364);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        // Test multiple clients
        DatagramClient client1 = new DatagramClient();
        DatagramClient client2 = new DatagramClient();

        byte[] response1 = client1.sendRequest("google.com", 7364);
        byte[] response2 = client2.sendRequest("facebook.com", 7364);

        try {
            assertNotNull("Response from client 1 should not be null", response1);
            assertNotNull("Response from client 2 should not be null", response2);
            System.out.println(ANSI_GREEN + "Success: Responses received from multiple clients." + ANSI_RESET);
        } catch (AssertionError e) {
            System.out.println(ANSI_RED + "Failure: " + e.getMessage() + ANSI_RESET);
            throw e;
        }
    }

    @Test
    public void testCaching() throws Exception {
        System.out.println("\nStarting test: Caching");

        String domain = "example.com";

        // Perform the first query
        DatagramClient client = new DatagramClient();
        byte[] firstResponse = client.sendRequest(domain, 7364);

        // Perform the second query (should be cached)
        byte[] secondResponse = client.sendRequest(domain, 7364);

        try {
            assertArrayEquals("Cached responses should be identical", firstResponse, secondResponse);
            System.out.println(ANSI_GREEN + "Success: Caching is functioning correctly." + ANSI_RESET);
        } catch (AssertionError e) {
            System.out.println(ANSI_RED + "Failure: " + e.getMessage() + ANSI_RESET);
            throw e;
        }
    }

    @Test
    public void testInvalidRequestHandling() throws Exception {
        System.out.println("\nStarting test: Invalid Request Handling");

        DatagramClient client = new DatagramClient();
        byte[] response = client.sendInvalidRequest(7364);

        // Log response details
        try {
            if (response != null) {
                ByteBuffer responseBuffer = ByteBuffer.wrap(response);
                short responseCode = (short) (responseBuffer.getShort(2) & 0xF); // Extract RCODE from DNS response
                System.out.println("Response received for invalid request with RCODE: " + responseCode);

                // Assert that the response code is a format error (1) or server failure (2)
                assertTrue("Response code should be a format error (1) or server failure (2)", responseCode == 1 || responseCode == 2);
                System.out.println(ANSI_GREEN + "Success: Invalid request handling is functioning correctly." + ANSI_RESET);
            } else {
                System.out.println(ANSI_RED + "Failure: No response received for invalid request." + ANSI_RESET);
                fail("No response received for invalid request.");
            }
        } catch (AssertionError e) {
            System.out.println(ANSI_RED + "Failure: " + e.getMessage() + ANSI_RESET);
            throw e;
        }
    }

    @Test
    public void testMaliciousRequestHandling() throws Exception {
        System.out.println("\nStarting test: Malicious Request Handling");

        DatagramClient client = new DatagramClient();
        byte[] response = client.sendMaliciousRequest(7364);

        // Check if the response is null or is an error response
        try {
            if (response != null) {
                ByteBuffer buffer = ByteBuffer.wrap(response);
                short rcode = (short) (buffer.getShort(2) & 0x0F); // Extract the RCODE

                assertTrue("Response to malicious request should be an error response", rcode != 0); // RCODE should not be 0 (NOERROR)
                System.out.println(ANSI_GREEN + "Success: Malicious request handling is functioning correctly." + ANSI_RESET);
            } else {
                System.out.println(ANSI_RED + "Failure: No response received for malicious request." + ANSI_RESET);
                fail("Response to malicious request should not be null");
            }
        } catch (AssertionError e) {
            System.out.println(ANSI_RED + "Failure: " + e.getMessage() + ANSI_RESET);
            throw e;
        }
    }
    
    @Test
    public void iterativeResolutionTest() throws Exception {
        System.out.println("\nStarting test: Iterative Resolution");
        
        try {
            NameServer ns = new NameServer();
            ns.setNameServer(InetAddress.getByName("a.root-servers.net"), 53); // Start with the root server
            InetAddress address = ns.resolveNSRecord("www.github.com");
            assertNotNull("Resolved IP address should not be null", address);
            System.out.println(ANSI_GREEN + "Success: Resolved IP: " + address.getHostAddress() + ANSI_RESET);
        } catch (Exception e) {
            System.out.println(ANSI_RED + "Failure: Exception occurred during iterative resolution test." + ANSI_RESET);
            e.printStackTrace();
            fail("Exception occurred during iterative resolution test.");
        }
    }

    @Test
    public void testCacheExpiration() throws Exception {
        System.out.println("\nStarting test: Cache Expiration");
    
        String domain = "expired-example.com";
    
        // Perform the first query
        DatagramClient client = new DatagramClient();
        byte[] firstResponse = client.sendRequest(domain, 7364);
    
        // Wait for the cache to expire
        System.out.print("Waiting for cache to expire:     ");
        for(int i = 0; i <= 10; i++)
        {
            int j = (i % 4) + 1;
            System.out.printf("\b\b\b%c%d%c", j, i, j);
            Thread.sleep(1000);
        }
        System.out.println("");

        // Perform the second query (should be a new request)
        byte[] secondResponse = client.sendRequest(domain, 7364);
    
        try {
            // Ensure that responses are different
            if (firstResponse != null && secondResponse != null) {
                assertNotEquals("Responses should differ after cache expiration", firstResponse, secondResponse);
                System.out.println(ANSI_GREEN + "Success: Cache expiration is functioning correctly." + ANSI_RESET);
            } else {
                fail("Responses should not be null");
            }
        } catch (AssertionError e) {
            System.out.println(ANSI_RED + "Failure: " + e.getMessage() + ANSI_RESET);
            throw e;
        }
    }
}

class DatagramClient {
    // Methods remain unchanged
    public byte[] sendRequest(String domain, int port) throws Exception {
        DatagramSocket socket = new DatagramSocket();
        byte[] queryPacket = buildQueryPacket(domain);
        DatagramPacket requestPacket = new DatagramPacket(queryPacket, queryPacket.length, InetAddress.getByName("localhost"), port);
        socket.send(requestPacket);

        byte[] buffer = new byte[512];
        DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);
        socket.receive(responsePacket);

        return buffer;
    }

    public byte[] sendInvalidRequest(int port) throws Exception {
        DatagramSocket socket = new DatagramSocket();
        byte[] invalidPacket = new byte[]{0, 0, 0, 0, 0, 0, 0, 0};
        DatagramPacket requestPacket = new DatagramPacket(invalidPacket, invalidPacket.length, InetAddress.getByName("localhost"), port);
        socket.send(requestPacket);

        try {
            byte[] buffer = new byte[512];
            DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);
            socket.receive(responsePacket);
            return buffer;
        } catch (SocketTimeoutException e) {
            return null;
        }
    }

    public byte[] sendMaliciousRequest(int port) throws Exception {
        DatagramSocket socket = new DatagramSocket();
        byte[] maliciousPacket = new byte[512];
        new Random().nextBytes(maliciousPacket);
        DatagramPacket requestPacket = new DatagramPacket(maliciousPacket, maliciousPacket.length, InetAddress.getByName("localhost"), port);
        socket.send(requestPacket);

        try {
            byte[] buffer = new byte[512];
            DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);
            socket.receive(responsePacket);
            return buffer;
        } catch (SocketTimeoutException e) {
            return null;
        }
    }

    private byte[] buildQueryPacket(String domain) {
        ByteBuffer buffer = ByteBuffer.allocate(512);
        int transactionID = new Random().nextInt(65536);
        buffer.putShort((short) transactionID);
        buffer.putShort((short) 0x0100);
        buffer.putShort((short) 1);
        buffer.putShort((short) 0);
        buffer.putShort((short) 0);
        buffer.putShort((short) 0);

        String[] labels = domain.split("\\.");
        for (String label : labels) {
            buffer.put((byte) label.length());
            for (char c : label.toCharArray()) {
                buffer.put((byte) c);
            }
        }
        buffer.put((byte) 0);
        buffer.putShort((short) 1);
        buffer.putShort((short) 1);

        return buffer.array();
    }
}
