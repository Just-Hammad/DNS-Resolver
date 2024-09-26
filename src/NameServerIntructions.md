# NameServer

## Overview

`NameServer` is a Java-based DNS server that handles incoming DNS queries, caches responses, and performs iterative DNS resolution. It is designed to work with a root DNS server and can be configured to listen on a specified port.

## Features

- Handles multiple simultaneous clients using UDP.
- Caches DNS responses to improve performance and reduce load on upstream servers.
- Performs iterative DNS resolution to resolve domain names.
- Returns appropriate error responses for invalid queries or failures.
- Can be customized and extended for specific DNS query handling.

## Prerequisites

- Java Development Kit (JDK) 8 or higher.
- Basic understanding of DNS and network programming.
- A root DNS server's IP address and port number.

## Setup


1. **Compile the Code**

   Compile the `NameServer.java` and any dependent files using the following command:

   ```bash
   javac NameServer.java
   ```

2. **Run the NameServer**

   Run the `NameServer` with the following command:

   ```bash
   java NameServer
   ```

   By default, the server will listen on port 53, but you can configure this by modifying the code.

## Usage

### Setting the Root DNS Server

Before starting the `NameServer`, you need to set the IP address and port of the root DNS server that will be used for iterative queries. This can be done programmatically:

```java
InetAddress rootServerIP = InetAddress.getByName("8.8.8.8"); // Example using Google DNS
int rootServerPort = 53; // Standard DNS port
nameServer.setNameServer(rootServerIP, rootServerPort);
```

### Handling Incoming Queries

Once the root server is set, start the `NameServer` to handle incoming DNS queries:

```java
int port = 53; // Default DNS port
nameServer.handleIncomingQueries(port);
```

### Cache Behavior

The server caches responses for 10 seconds (default). You can adjust the cache expiration time by modifying the `CACHE_EXPIRATION_TIME` constant.

### Error Handling

The `NameServer` implementation includes error handling for:

- Invalid or malformed queries.
- Failed DNS resolutions.
- Server failures.

### DNS Query Resolution

The server performs iterative DNS query resolution starting from the root DNS server. It follows referrals to other DNS servers until it resolves the query or encounters an error.

## Customization

You can customize the following aspects of the `NameServer`:

- **Cache Expiration**: Modify the `CACHE_EXPIRATION_TIME` constant.
- **DNS Query Types**: Extend the handling of different DNS query types (A, NS, MX, etc.).
- **Error Responses**: Customize the error response handling in the `sendErrorResponse` method.

## Testing

You can test the `NameServer` by pointing a DNS client or resolver to the server's IP address and port. Use tools like `dig` or `nslookup` to send DNS queries to the server.

Example using `dig`:

```bash
dig @localhost -p 53 example.com
```

## Troubleshooting

- Ensure that the server is listening on the correct port.
- Verify that the root DNS server IP and port are correctly configured.
- Check for any exceptions or error messages in the server logs.
