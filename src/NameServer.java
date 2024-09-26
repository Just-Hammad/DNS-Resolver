import java.net.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;


interface NameServerInterface {
	public void setNameServer(InetAddress ipAddress, int port);

	public void handleIncomingQueries(int port) throws Exception;
}

public class NameServer implements NameServerInterface {
	private InetAddress rootServerIP;
	private int rootServerPort;
	
	private static final long CACHE_EXPIRATION_TIME = 10000; // 10 seconds
	private final ConcurrentHashMap<String, CacheEntry> dnsCache = new ConcurrentHashMap<>();
	private Random random = new Random();

	@Override
	public void setNameServer(InetAddress ipAddress, int port) {
		this.rootServerIP = ipAddress;
		this.rootServerPort = port;
	}

	@Override
	public void handleIncomingQueries(int port) throws Exception {
		try (DatagramSocket serverSocket = new DatagramSocket(port)) {
			while (true) {
				byte[] receiveData = new byte[512];
				DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
				serverSocket.receive(receivePacket);
				new Thread(() -> processQuery(receivePacket, serverSocket)).start();
			}
		} catch (Exception e) {
			System.out.println("Server error: " + e.getMessage());
			throw new Exception("Failed to start DNS server", e);
		}
	}



private static class CacheEntry {
    byte[] data;
    long timestamp;

    CacheEntry(byte[] data) {
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    boolean isExpired() {
        return System.currentTimeMillis() - timestamp > CACHE_EXPIRATION_TIME;
    }
}

private void processQuery(DatagramPacket packet, DatagramSocket serverSocket) {
    if (packet == null) {
        System.out.println("Received null packet");
        return;
    }

    byte[] data = packet.getData();
    if (data == null) {
        System.out.println("Packet data is null");
        return;
    }

    try {
        // Check if the data length is reasonable for a DNS packet
        if (data.length < 12) { // DNS header is 12 bytes
            System.out.println("Data length is too short for DNS packet");
            sendErrorResponse(packet, serverSocket, 2); // Format error
            return;
        }

        if (!isValidQuery(data)) {
            System.out.println("Invalid query received");
            sendErrorResponse(packet, serverSocket, 2); // Format error
            return;
        }

        byte[] requestData = packet.getData();
        InetAddress clientAddress = packet.getAddress();
        int clientPort = packet.getPort();

		String query;
		try {
			query = extractQuery(ByteBuffer.wrap(requestData));
		} catch (Exception e) {
			System.out.println("Failed to extract query: " + e.getMessage());
			sendErrorResponse(packet, serverSocket, 2); // Format error
			return;
		}
		

        byte[] response;

        CacheEntry cacheEntry = dnsCache.get(query);
        if (cacheEntry != null && !cacheEntry.isExpired()) {
            response = cacheEntry.data;
        } else {
            try {
                response = performIterativeQuery(query);
                if (response == null) {
                    System.out.println("Failed to get response for query: " + query);
                    sendErrorResponse(packet, serverSocket, 2); // Server failure
                    return;
                } else {
                    dnsCache.put(query, new CacheEntry(response));
                }
            } catch (Exception e) {
                System.out.println("Error performing iterative query: " + e.getMessage());
                sendErrorResponse(packet, serverSocket, 2); // Server failure
                return;
            }
        }

        DatagramPacket responsePacket = new DatagramPacket(response, response.length, clientAddress, clientPort);
        serverSocket.send(responsePacket);
    } catch (Exception e) {
        System.out.println("Error processing query: " + e.getMessage());
        e.printStackTrace();
        sendErrorResponse(packet, serverSocket, 2); // Server failure
    }
}


private void sendErrorResponse(DatagramPacket requestPacket, DatagramSocket serverSocket, int rcode) {
    try {
        ByteBuffer buffer = ByteBuffer.allocate(512);
        int transactionID = ByteBuffer.wrap(requestPacket.getData()).getShort(0);
        buffer.putShort((short) transactionID); // Copy transaction ID
        buffer.putShort((short) (0x8000 | (rcode & 0xF))); // Set response flag and RCODE
        buffer.putShort((short) 0); // Questions
        buffer.putShort((short) 0); // Answer RRs
        buffer.putShort((short) 0); // Authority RRs
        buffer.putShort((short) 0); // Additional RRs

        DatagramPacket errorPacket = new DatagramPacket(buffer.array(), buffer.position(), requestPacket.getAddress(), requestPacket.getPort());
        serverSocket.send(errorPacket);
    } catch (Exception e) {
        System.out.println("Failed to send error response: " + e.getMessage());
    }
}



	private byte[] performIterativeQuery(String query) {
		try {
			InetAddress dnsServer = rootServerIP; // Start with the root server
			int dnsPort = rootServerPort; // Standard DNS port
			byte[] queryPacket = buildQueryPacket(query); // Build the DNS query packet

			while (true) {
				try (DatagramSocket socket = new DatagramSocket()) {
					socket.setSoTimeout(2000); // Set timeout for response
					DatagramPacket requestPacket = new DatagramPacket(queryPacket, queryPacket.length, dnsServer,
							dnsPort);
					socket.send(requestPacket);

					byte[] buffer = new byte[512];
					DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);
					socket.receive(responsePacket); // Receive the DNS response

					DNSResponse dnsResponse = parseResponse(buffer);
					if (dnsResponse.rcode == 0 && dnsResponse.answerCount > 0) { // No error and there are answers
						return buffer;
					} else if (dnsResponse.rcode == 3) { // Name Error means no such name exists
						return buffer; // Returning response to indicate name error
					} else {
						InetAddress nextServer = getNextServer(buffer);
						if (nextServer != null) {
							dnsServer = nextServer; // Update the server for the next query
						} else {
							throw new Exception("No further DNS servers available for querying.");
						}
					}
				}
			}
		} catch (Exception e) {
			System.out.println("Error during DNS resolution: " + e.getMessage());
			return null;
		}
	}

	private InetAddress getNextServer(byte[] response) throws Exception {
		ByteBuffer buffer = ByteBuffer.wrap(response);

		// Skip the header
		buffer.position(12); // DNS header is always 12 bytes

		// Skip the question section
		int questionCount = buffer.getShort(4); // Number of questions
		for (int i = 0; i < questionCount; i++) {
			skipDomainName(buffer); // Skip the domain name in the question section
			buffer.getShort(); // Type
			buffer.getShort(); // Class
		}

		// Skip the answer section
		int answerCount = buffer.getShort(6);
		for (int i = 0; i < answerCount; i++) {
			skipResourceRecord(buffer);
		}

		// Parse the authority section
		int authorityCount = buffer.getShort(8);
		for (int i = 0; i < authorityCount; i++) {
			skipDomainName(buffer); // Domain name
			int type = buffer.getShort();
			buffer.getShort(); // Class
			buffer.getInt(); // TTL
			int dataLength = buffer.getShort(); // Data length
			if (type == 2) { // NS record type
				String nsName = readDomainName(buffer, response); // Read the NS domain name
				return resolveNSRecord(nsName);
			} else {
				buffer.position(buffer.position() + dataLength); // Skip this record if not NS
			}
		}

		return null;
	}

	private void skipResourceRecord(ByteBuffer buffer) throws Exception {
		skipDomainName(buffer);
		buffer.getShort(); // Type
		buffer.getShort(); // Class
		buffer.getInt(); // TTL
		int dataLength = buffer.getShort(); // Data length
		buffer.position(buffer.position() + dataLength); // Skip over data
	}

	private String readDomainName(ByteBuffer buffer, byte[] data) {
		StringBuilder result = new StringBuilder();
		boolean jumped = false;
		int initialPosition = buffer.position();

		while (true) {
			byte length = buffer.get();
			if (length == 0) { // End of name
				break;
			}
			if ((length & 0xC0) == 0xC0) { // Compression pointer
				if (!jumped) {
					buffer.position(buffer.get() + ((length & 0x3F) << 8));
					jumped = true;
				} else {
					buffer.position(buffer.get() + ((length & 0x3F) << 8));
				}
				continue;
			}
			if (result.length() > 0) {
				result.append('.');
			}
			for (int i = 0; i < length; i++) {
				result.append((char) buffer.get());
			}
		}

		if (jumped) {
			buffer.position(initialPosition + 2); // Reset after reading compression pointer
		}

		return result.toString();
	}

	// private int parseResponseCode(byte[] response) {
	// // Extract response code from the DNS response header
	// // The RCODE is the lower 4 bits of the 6th byte
	// return response[3] & 0x0F; // Corrected from the previous example to properly
	// access RCODE
	// }

	protected DNSResponse parseResponse(byte[] response) throws Exception {
		ByteBuffer buffer = ByteBuffer.wrap(response);

		int transactionID = buffer.getShort() & 0xFFFF;
		int flags = buffer.getShort() & 0xFFFF;
		int rcode = flags & 0x0F; // Last 4 bits of the flags indicate RCODE
		int questionCount = buffer.getShort() & 0xFFFF;
		int answerCount = buffer.getShort() & 0xFFFF;
		int authorityCount = buffer.getShort() & 0xFFFF;
		int additionalCount = buffer.getShort() & 0xFFFF;

		DNSResponse dnsResponse = new DNSResponse(transactionID, rcode, questionCount, answerCount, authorityCount,
				additionalCount);

		// Parsing each section with appropriate methods
		parseQuestions(buffer, questionCount);
		parseResourceRecords(buffer, answerCount, dnsResponse.answers);
		parseResourceRecords(buffer, authorityCount, dnsResponse.authorities);
		parseResourceRecords(buffer, additionalCount, dnsResponse.additionals);

		return dnsResponse;
	}

	private void parseQuestions(ByteBuffer buffer, int count) throws Exception {
		for (int i = 0; i < count; i++) {
			skipDomainName(buffer); // Domain name
			buffer.getShort(); // Type
			buffer.getShort(); // Class
		}
	}

	private void parseResourceRecords(ByteBuffer buffer, int count, List<ResourceRecord> records) {
		for (int i = 0; i < count; i++) {
			records.add(parseResourceRecord(buffer));
		}
	}

	private ResourceRecord parseResourceRecord(ByteBuffer buffer) {
		String name = readDomainName(buffer, buffer.array()); // Assume implementation from previous
		int type = buffer.getShort();
		int cls = buffer.getShort();
		long ttl = buffer.getInt() & 0xFFFFFFFFL; // Convert to unsigned long
		int dataLength = buffer.getShort();
		byte[] data = new byte[dataLength];
		buffer.get(data);

		return new ResourceRecord(name, type, cls, ttl, data);
	}

	// Assuming a class structure for handling DNS responses
	class DNSResponse {
		int transactionID;
		int rcode;
		int questionCount;
		int answerCount;
		int authorityCount;
		int additionalCount;
		List<ResourceRecord> answers = new ArrayList<>();
		List<ResourceRecord> authorities = new ArrayList<>();
		List<ResourceRecord> additionals = new ArrayList<>();

		public DNSResponse(int transactionID, int rcode, int questionCount, int answerCount, int authorityCount,
				int additionalCount) {
			this.transactionID = transactionID;
			this.rcode = rcode;
			this.questionCount = questionCount;
			this.answerCount = answerCount;
			this.authorityCount = authorityCount;
			this.additionalCount = additionalCount;
		}

		void addAnswer(ResourceRecord record) {
			answers.add(record);
		}

		void addAuthority(ResourceRecord record) {
			authorities.add(record);
		}

		void addAdditional(ResourceRecord record) {
			additionals.add(record);
		}
	}

	// Resource record structure
	class ResourceRecord {
		String name;
		int type;
		int cls;
		long ttl;
		byte[] data;

		public ResourceRecord(String name, int type, int cls, long ttl, byte[] data) {
			this.name = name;
			this.type = type;
			this.cls = cls;
			this.ttl = ttl;
			this.data = data;
		}
	}

	private byte[] buildQueryPacket(String domain) {
		ByteBuffer buffer = ByteBuffer.allocate(512);
		int transactionID = random.nextInt(65536);
		buffer.putShort((short) transactionID);
		buffer.putShort((short) 0x0100); // Standard query with recursion desired
		buffer.putShort((short) 1); // One question
		buffer.putShort((short) 0); // No answers in query
		buffer.putShort((short) 0); // No authority records in query
		buffer.putShort((short) 0); // No additional records in query

		String[] labels = domain.split("\\.");
		for (String label : labels) {
			buffer.put((byte) label.length());
			for (char c : label.toCharArray()) {
				buffer.put((byte) c);
			}
		}
		buffer.put((byte) 0); // End of domain name
		buffer.putShort((short) 1); // Type A
		buffer.putShort((short) 1); // Class IN

		return buffer.array();
	}

	private boolean isValidQuery(byte[] requestData) {
		if (requestData.length < 12) {
			// DNS header is at least 12 bytes
			return false;
		}
	
		ByteBuffer buffer = ByteBuffer.wrap(requestData);
		short id = buffer.getShort(); // Transaction ID
		short flags = buffer.getShort(); // Flags
		short qdCount = buffer.getShort(); // Number of questions
		short anCount = buffer.getShort(); // Number of answer RRs
		short nsCount = buffer.getShort(); // Number of authority RRs
		short arCount = buffer.getShort(); // Number of additional RRs
	
		// Basic header validation
		if (qdCount <= 0 || anCount < 0 || nsCount < 0 || arCount < 0) {
			return false;
		}
	
		// Validate the question section
		int currentPosition = 12; // Start of the question section
		for (int i = 0; i < qdCount; i++) {
			buffer.position(currentPosition);
			String query = extractQuery(buffer);
			if (query == null || query.isEmpty()) {
				return false;
			}
	
			// Validate the query type and class
			currentPosition = buffer.position(); // Update current position after extracting the query
	
			if (currentPosition + 4 > requestData.length) {
				return false; // Not enough data for QTYPE and QCLASS
			}
	
			ByteBuffer queryBuffer = ByteBuffer.wrap(requestData, currentPosition, 4);
			int qType = queryBuffer.getShort() & 0xFFFF; // Query Type
			int qClass = queryBuffer.getShort() & 0xFFFF; // Query Class
	
			if (!isValidQueryType(qType) || qClass != 1) { // 1 is the class for Internet
				return false;
			}
	
			currentPosition += 4; // Move past the QTYPE and QCLASS
		}
	
		return true;
	}
	
	
	private boolean isValidQueryType(int qType) {
		// Valid types according to DNS standards (e.g., A, MX, NS)
		return qType == 1 || qType == 2 || qType == 5 || qType == 15; // Example types
	}
	

	private String extractQuery(ByteBuffer buffer) {
		buffer.position(12); // Skip header
		StringBuilder domainName = new StringBuilder();
		byte length;
		while ((length = buffer.get()) != 0) {
			if (domainName.length() > 0) {
				domainName.append('.');
			}
			for (int i = 0; i < length; i++) {
				domainName.append((char) buffer.get());
			}
		}
		return domainName.toString();
	}
	

	/**
	 * Manually construct and send a DNS query to resolve an NS record's domain name
	 * to an IP.
	 * 
	 * @param nsName The NS domain name to resolve.
	 * @return The IP address of the NS server.
	 * @throws Exception If resolving fails.
	 */
	protected InetAddress resolveNSRecord(String nsName) throws Exception {
		byte[] query = buildDNSQuery(nsName);
		DatagramSocket socket = new DatagramSocket();
		socket.setSoTimeout(5000); // Set a timeout for the response

		// Send DNS query to a known DNS server (e.g., Google's 8.8.8.8)
		InetAddress dnsServer = InetAddress.getByName("8.8.8.8");
		DatagramPacket packet = new DatagramPacket(query, query.length, dnsServer, 53);
		socket.send(packet);

		// Receive the DNS response
		byte[] buffer = new byte[512];
		DatagramPacket response = new DatagramPacket(buffer, buffer.length);
		socket.receive(response);
		socket.close();

		// Parse the DNS response to extract the IP address
		return parseDNSResponse(buffer);
	}

	/**
	 * Build a DNS query for the given domain name.
	 * 
	 * @param domain The domain name to query.
	 * @return The byte array representing the DNS query.
	 */
	private byte[] buildDNSQuery(String domain) {
		ByteBuffer buffer = ByteBuffer.allocate(512);
		buffer.putShort((short) 0x1234); // Transaction ID
		buffer.putShort((short) 0x0100); // Flags: standard query
		buffer.putShort((short) 1); // Questions count
		buffer.putShort((short) 0); // Answer RRs count
		buffer.putShort((short) 0); // Authority RRs count
		buffer.putShort((short) 0); // Additional RRs count

		// Query section
		for (String part : domain.split("\\.")) {
			buffer.put((byte) part.length());
			buffer.put(part.getBytes());
		}
		buffer.put((byte) 0); // End of domain name
		buffer.putShort((short) 0x0001); // Type: A
		buffer.putShort((short) 0x0001); // Class: IN

		return buffer.array();
	}

	/**
	 * Parse the DNS response to extract the IP address.
	 * 
	 * @param response The byte array containing the DNS response.
	 * @return The resolved IP address.
	 * @throws Exception If parsing fails or no address is found.
	 */

	private InetAddress parseDNSResponse(byte[] response) throws Exception {
		ByteBuffer buffer = ByteBuffer.wrap(response);

		if (buffer.limit() < 12)
			throw new Exception("Response too short to be valid DNS response");

		// Skip DNS header section
		buffer.position(12); // Move past the header

		// Parse question section safely
		int questionCount = (buffer.getShort(4) & 0xFFFF);
		for (int i = 0; i < questionCount; i++) {
			skipDomainName(buffer);
			buffer.position(buffer.position() + 4); // Skip type and class, assuming they exist
		}

		// Answer section
		int answerCount = (buffer.getShort(6) & 0xFFFF);
		if (answerCount == 0)
			throw new Exception("No answers in DNS response");

		for (int i = 0; i < answerCount; i++) {
			skipDomainName(buffer);
			if (buffer.remaining() < 10)
				throw new Exception("Incomplete record in response");

			int type = buffer.getShort();
			buffer.getShort(); // Class
			buffer.getInt(); // TTL
			int dataLength = buffer.getShort();
			if (type == 1 && dataLength == 4) { // Type A, IPv4
				if (buffer.remaining() < dataLength)
					throw new Exception("Declared length exceeds buffer limit");
				byte[] ipBytes = new byte[4];
				buffer.get(ipBytes);
				return InetAddress.getByAddress(ipBytes);
			} else {
				if (buffer.remaining() < dataLength)
					throw new Exception("Declared length exceeds buffer limit");
				buffer.position(buffer.position() + dataLength); // Skip other record types
			}
		}

		throw new Exception("No valid A record found");
	}

	private void skipDomainName(ByteBuffer buffer) throws Exception {
		while (buffer.remaining() > 0) {
			byte length = buffer.get();
			if (length == 0)
				return; // End of domain name
			if ((length & 0xC0) == 0xC0) { // Compression pointer
				buffer.get(); // Skip the compressed offset byte
				return;
			}
			if (buffer.remaining() < length)
				throw new Exception("Invalid domain name length");
			buffer.position(buffer.position() + length);
		}
		throw new Exception("Domain name parsing exceeded buffer limit");
	}

}
