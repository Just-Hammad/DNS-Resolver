import java.net.*;
import java.nio.ByteBuffer;

interface StubResolverInterface {
	void setNameServer(InetAddress ipAddress, int port);

	InetAddress recursiveResolveAddress(String domainName) throws Exception;

	String recursiveResolveText(String domainName) throws Exception;

	String recursiveResolveName(String domainName, int type) throws Exception;
}

public class StubResolver implements StubResolverInterface {

	private InetAddress dnsServer;
	private int dnsPort;

	@Override
	public void setNameServer(InetAddress ipAddress, int port) {
		this.dnsServer = ipAddress;
		this.dnsPort = port;
	}

	@Override
	public InetAddress recursiveResolveAddress(String domainName) throws Exception {
		if (domainName == null || domainName.trim().isEmpty()) {
			throw new IllegalArgumentException("Invalid query: Domain name cannot be empty");
		}
		if (domainName.matches(".*[\\x00-\\x1F].*")) {  // Regex to check for control characters
			throw new IllegalArgumentException("Invalid or malicious query detected: Malformed domain name.");
		}
		ByteBuffer buffer = recursiveResolve(domainName, 1); // Type A
		if (buffer == null) {
			throw new Exception("DNS resolution failed: No response received");
		}
		return parseInetAddressFromBuffer(buffer); // Parse and return the InetAddress
	}

	@Override
	public String recursiveResolveText(String domainName) throws Exception {
		ByteBuffer buffer = recursiveResolve(domainName, 16); // Type TXT
		if (buffer == null) {
			throw new Exception("DNS resolution failed: No response received for TXT record");
		}
		return extractTextFromDNSResponse(buffer);
	}

	@Override
	public String recursiveResolveName(String domainName, int type) throws Exception {
		ByteBuffer buffer = recursiveResolve(domainName, type);
		if (buffer == null) {
			throw new Exception("DNS resolution failed: No response received for record type: " + type);
		}
		return extractDomainName(buffer);
	}

	private ByteBuffer recursiveResolve(String domainName, int recordType) throws Exception {
		byte[] queryPacket = buildDNSQuery(domainName, recordType);
		DatagramSocket socket = new DatagramSocket();
		socket.setSoTimeout(5000); // 5 seconds timeout

		DatagramPacket sendPacket = new DatagramPacket(queryPacket, queryPacket.length, dnsServer, dnsPort);
		socket.send(sendPacket);

		byte[] responseBuffer = new byte[512];
		DatagramPacket receivePacket = new DatagramPacket(responseBuffer, responseBuffer.length);
		try {
			socket.receive(receivePacket);
		} catch (SocketTimeoutException e) {
			throw new Exception("DNS server timed out");
		} finally {
			socket.close();
		}

		ByteBuffer buffer = ByteBuffer.wrap(responseBuffer);
		int responseCode = (buffer.getShort(2) & 0xF);
		if (responseCode != 0) {
			throw new Exception("DNS query failed with response code: " + responseCode);
		}
		if (!isExpectedTypeInResponse(buffer, recordType)) {
			throw new Exception("No relevant answer found in DNS response for record type: " + recordType);
		}
		return buffer;
	}

	private byte[] buildDNSQuery(String domainName, int recordType) {
		ByteBuffer buffer = ByteBuffer.allocate(512);
		buffer.putShort((short) 0x1234); // Transaction ID
		buffer.putShort((short) 0x0100); // Flags: standard query with recursion desired
		buffer.putShort((short) 1); // Questions count
		buffer.putShort((short) 0); // Answer RRs count
		buffer.putShort((short) 0); // Authority RRs count
		buffer.putShort((short) 0); // Additional RRs count

		for (String part : domainName.split("\\.")) {
			buffer.put((byte) part.length());
			buffer.put(part.getBytes());
		}
		buffer.put((byte) 0); // End of domain name
		buffer.putShort((short) recordType); // Record type (e.g., A, TXT, MX, etc.)
		buffer.putShort((short) 1); // Class: IN (Internet)

		return buffer.array();
	}

	private boolean isExpectedTypeInResponse(ByteBuffer buffer, int expectedType) throws Exception {
		buffer.position(12); // Skip DNS header

		while (buffer.get() != 0) { // Skip question section
			// Skip domain name in question
		}
		buffer.position(buffer.position() + 4); // Skip type and class in question

		// Parse the answer section
		while (buffer.hasRemaining()) {
			buffer.getShort(); // Skip name pointer
			int type = buffer.getShort();
			buffer.getShort(); // Class
			buffer.getInt(); // TTL
			int dataLength = buffer.getShort();

			if (dataLength < 0 || dataLength > buffer.remaining()) {
				throw new IllegalArgumentException("Data length exceeds buffer limit or is invalid: " + dataLength);
			}

			if (type == expectedType) {
				return true; // Found the expected type
			} else {
				buffer.position(buffer.position() + dataLength); // Skip data section of the record
			}
		}
		return false; // No relevant answer found
	}

	private InetAddress parseInetAddressFromBuffer(ByteBuffer buffer) throws Exception {
		buffer.position(12); // Start parsing after the DNS header and question section
		while (buffer.get() != 0) { // Skip the question section
			// Skip domain name in the question
		}
		buffer.position(buffer.position() + 4); // Skip the question's type and class

		while (buffer.hasRemaining()) {
			buffer.getShort(); // Skip name pointer
			int type = buffer.getShort(); // Record type
			buffer.getShort(); // Class
			buffer.getInt(); // TTL
			int dataLength = buffer.getShort() & 0xFFFF; // Data length

			if (type == 1 && dataLength == 4) { // Type A and data length is 4 bytes
				byte[] ipBytes = new byte[4];
				buffer.get(ipBytes); // Extract the IP address bytes
				return InetAddress.getByAddress(ipBytes);
			} else {
				buffer.position(buffer.position() + dataLength); // Skip irrelevant data
			}
		}

		throw new Exception("No A record found in the DNS response");
	}

	private String extractDomainName(ByteBuffer buffer) {
		StringBuilder domainName = new StringBuilder();
		while (true) {
			byte length = buffer.get();
			if (length == 0)
				break;
			if ((length & 0xC0) == 0xC0) { // Name compression
				int pointer = ((length & 0x3F) << 8) | (buffer.get() & 0xFF);
				extractDomainNameFromPointer(buffer, pointer, domainName);
				break;
			} else {
				for (int i = 0; i < length; i++) {
					domainName.append((char) buffer.get());
				}
				domainName.append('.');
			}
		}
		return domainName.toString();
	}

	private void extractDomainNameFromPointer(ByteBuffer buffer, int pointer, StringBuilder domainName) {
		int originalPosition = buffer.position();
		buffer.position(pointer);
		while (true) {
			byte length = buffer.get();
			if (length == 0)
				break;
			if ((length & 0xC0) == 0xC0) { // Compression pointer
				int nestedPointer = ((length & 0x3F) << 8) | (buffer.get() & 0xFF);
				extractDomainNameFromPointer(buffer, nestedPointer, domainName);
				break;
			} else {
				for (int i = 0; i < length; i++) {
					domainName.append((char) buffer.get());
				}
				domainName.append('.');
			}
		}
		buffer.position(originalPosition); // Restore buffer position
	}

	private String extractTextFromDNSResponse(ByteBuffer buffer) {
		if (buffer != null && buffer.hasRemaining()) {
			int length = buffer.get() & 0xFF;
			byte[] textBytes = new byte[length];
			buffer.get(textBytes);
			return new String(textBytes);
		}
		return null;
	}
}
