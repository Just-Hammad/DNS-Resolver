# StubResolver

## Overview

The `StubResolver` is a simple DNS client implementation in Java that performs recursive DNS lookups. It allows you to resolve various DNS record types, such as `A`, `TXT`, and other records, by communicating directly with a DNS server using UDP.

## Features

- **Set DNS Server**: Configure the DNS server IP address and port to be used for queries.
- **Resolve Address**: Retrieve the IPv4 address (A record) for a given domain name.
- **Resolve Text**: Retrieve the text (TXT record) associated with a given domain name.
- **Resolve Name**: Retrieve the resolved domain name based on the specified DNS record type.

## Methods

### `void setNameServer(InetAddress ipAddress, int port)`

Sets the DNS server's IP address and port that will be used for DNS queries.

- **Parameters:**
  - `ipAddress`: The IP address of the DNS server.
  - `port`: The port number of the DNS server.

### `InetAddress recursiveResolveAddress(String domainName)`

Performs a recursive DNS lookup for the A record (IPv4 address) of the specified domain name.

- **Parameters:**
  - `domainName`: The domain name to resolve.
- **Returns:**
  - The `InetAddress` of the domain name.
- **Throws:**
  - `IllegalArgumentException`: If the domain name is empty or contains invalid characters.
  - `Exception`: If the DNS resolution fails.

### `String recursiveResolveText(String domainName)`

Performs a recursive DNS lookup for the TXT record of the specified domain name.

- **Parameters:**
  - `domainName`: The domain name to resolve.
- **Returns:**
  - The TXT record as a `String`.
- **Throws:**
  - `Exception`: If the DNS resolution fails or no response is received.

### `String recursiveResolveName(String domainName, int type)`

Performs a recursive DNS lookup for a specific DNS record type of the specified domain name.

- **Parameters:**
  - `domainName`: The domain name to resolve.
  - `type`: The DNS record type (e.g., A, TXT, MX).
- **Returns:**
  - The resolved domain name as a `String`.
- **Throws:**
  - `Exception`: If the DNS resolution fails or no response is received.

## Usage Example

```java
public class Main {
    public static void main(String[] args) throws Exception {
        StubResolver resolver = new StubResolver();
        resolver.setNameServer(InetAddress.getByName("8.8.8.8"), 53);

        // Resolve A record (IPv4 address)
        InetAddress address = resolver.recursiveResolveAddress("example.com");
        System.out.println("IPv4 Address: " + address.getHostAddress());

        // Resolve TXT record
        String txtRecord = resolver.recursiveResolveText("example.com");
        System.out.println("TXT Record: " + txtRecord);

        // Resolve any other DNS record
        String cnameRecord = resolver.recursiveResolveName("example.com", 5); // CNAME record type
        System.out.println("CNAME Record: " + cnameRecord);
    }
}
```

## Internal Methods

- **`ByteBuffer recursiveResolve(String domainName, int recordType)`**: Builds and sends the DNS query, then receives and processes the response.
- **`byte[] buildDNSQuery(String domainName, int recordType)`**: Constructs the DNS query packet.
- **`boolean isExpectedTypeInResponse(ByteBuffer buffer, int expectedType)`**: Checks if the DNS response contains the expected record type.
- **`InetAddress parseInetAddressFromBuffer(ByteBuffer buffer)`**: Extracts and returns the IP address from the DNS response buffer.
- **`String extractDomainName(ByteBuffer buffer)`**: Extracts the domain name from the DNS response buffer.
- **`String extractTextFromDNSResponse(ByteBuffer buffer)`**: Extracts the TXT record content from the DNS response buffer.

## Notes

- Ensure that the DNS server you use supports recursive requests.
- The resolver handles both successful and unsuccessful queries and throws appropriate exceptions in case of failures.
