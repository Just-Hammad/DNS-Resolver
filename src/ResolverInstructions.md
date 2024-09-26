# Resolver

## Overview

`Resolver` is a Java-based DNS resolver that performs iterative DNS resolution. It can query different DNS record types such as A, TXT, and CNAME. The resolver sends DNS queries using UDP and processes the responses to extract the required information.

## Features

- Supports iterative DNS resolution for A (IPv4 address), TXT, and CNAME records.
- Handles CNAME loops by detecting and preventing infinite resolution.
- Allows setting a custom DNS server for query resolution.
- Customizable timeout for receiving DNS responses.

## Prerequisites

- Java Development Kit (JDK) 8 or higher.
- Basic understanding of DNS and network programming.
- A DNS server's IP address and port number (e.g., a root DNS server or any other DNS server).

## Setup

1. **Compile the Code**

   Compile the `Resolver.java` and any dependent files using the following command:

   ```bash
   javac Resolver.java
   ```

## Usage

### Setting the DNS Server

Before performing any DNS resolution, you need to set the IP address and port of the DNS server that the resolver will use. This can be done as follows:

```java
Resolver resolver = new Resolver();
InetAddress dnsServerIP = InetAddress.getByName("8.8.8.8"); // Example using Google DNS
int dnsServerPort = 53; // Standard DNS port
resolver.setNameServer(dnsServerIP, dnsServerPort);
```

### Performing DNS Resolution

The `Resolver` class provides methods to resolve different types of DNS records:

- **A Record (IPv4 Address)**

   ```java
   InetAddress ipAddress = resolver.iterativeResolveAddress("example.com");
   System.out.println("Resolved IP Address: " + ipAddress);
   ```

- **TXT Record**

   ```java
   String txtRecord = resolver.iterativeResolveText("example.com");
   System.out.println("Resolved TXT Record: " + txtRecord);
   ```

- **CNAME Record**

   ```java
   String cname = resolver.iterativeResolveName("example.com", 5); // 5 for CNAME
   System.out.println("Resolved CNAME: " + cname);
   ```

### Handling CNAME Loops

The resolver is designed to detect and prevent CNAME loops. If a loop is detected, an exception is thrown with a descriptive message.

### Customization

You can customize the following aspects of the `Resolver`:

- **Timeout for Receiving Responses**: The timeout is set to 5 seconds by default. You can adjust it by modifying the `socket.setSoTimeout(5000);` line in the `sendQuery` method.
- **Query Types**: The resolver currently supports A, TXT, and CNAME record types. You can extend the `Resolver` class to handle additional DNS record types.

## Testing

You can test the `Resolver` class by invoking the methods provided and pointing it to a known DNS server like Google DNS (`8.8.8.8`) or Cloudflare DNS (`1.1.1.1`).

Example using `iterativeResolveAddress`:

```java
public static void main(String[] args) {
    try {
        Resolver resolver = new Resolver();
        resolver.setNameServer(InetAddress.getByName("8.8.8.8"), 53);
        InetAddress ipAddress = resolver.iterativeResolveAddress("example.com");
        System.out.println("Resolved IP Address: " + ipAddress.getHostAddress());
    } catch (Exception e) {
        e.printStackTrace();
    }
}
```

## Troubleshooting

- Ensure that the DNS server IP and port are correctly configured.
- Check for any exceptions or error messages during the resolution process.
- Verify network connectivity to the DNS server.

## License

This project is licensed under the MIT License.
