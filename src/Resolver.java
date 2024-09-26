import java.io.*;
import java.net.*;
import java.util.*;

// Interface definition
interface ResolverInterface {
    void setNameServer(InetAddress ipAddress, int port);
    InetAddress iterativeResolveAddress(String domainName) throws Exception;
    String iterativeResolveText(String domainName) throws Exception;
    String iterativeResolveName(String domainName, int type) throws Exception;
}

public class Resolver implements ResolverInterface {
    private InetAddress nameServer;
    private static final int DNS_PORT = 53;

    @Override
    public void setNameServer(InetAddress ipAddress, int port) {
        this.nameServer = ipAddress;
    }

    @Override
    public InetAddress iterativeResolveAddress(String domainName) throws Exception {
        byte[] response = queryDNS(domainName, 1); // 1 for A record
        return extractAddress(response);
    }

    @Override
    public String iterativeResolveText(String domainName) throws Exception {
        byte[] response = queryDNS(domainName, 16); // 16 for TXT record
        return extractText(response);
    }

    @Override
    public String iterativeResolveName(String domainName, int type) throws Exception {
        byte[] response = queryDNS(domainName, type);
        return extractCNAME(response);
    }

    private byte[] queryDNS(String domain, int type) throws IOException {
        DatagramSocket socket = new DatagramSocket();
        socket.setSoTimeout(5000); // 5 seconds timeout
        byte[] request = buildRequest(domain, type);
        DatagramPacket packet = new DatagramPacket(request, request.length, nameServer, DNS_PORT);
        socket.send(packet);

        byte[] response = new byte[512];
        DatagramPacket responsePacket = new DatagramPacket(response, response.length);
        socket.receive(responsePacket);
        socket.close();

        return responsePacket.getData();
    }

    private byte[] buildRequest(String domain, int type) {
        byte[] header = new byte[12];
        header[0] = (byte) 0xAA; // ID
        header[1] = (byte) 0xAA; // ID
        header[2] = (byte) 0x01; // Flags
        header[3] = (byte) 0x00; // Flags
        header[4] = (byte) 0x00; // QDCOUNT (1)
        header[5] = (byte) 0x01; // QDCOUNT (1)
        header[6] = (byte) 0x00; // ANCOUNT (0)
        header[7] = (byte) 0x00; // ANCOUNT (0)
        header[8] = (byte) 0x00; // NSCOUNT (0)
        header[9] = (byte) 0x00; // NSCOUNT (0)
        header[10] = (byte) 0x00; // ARCOUNT (0)
        header[11] = (byte) 0x00; // ARCOUNT (0)

        String[] labels = domain.split("\\.");
        byte[] qName = new byte[domain.length() + labels.length];
        int pos = 0;
        for (String label : labels) {
            qName[pos++] = (byte) label.length();
            for (char c : label.toCharArray()) {
                qName[pos++] = (byte) c;
            }
        }
        qName[pos++] = 0x00; // End of domain name

        byte[] qType = new byte[] { 0x00, (byte) type };
        byte[] qClass = new byte[] { 0x00, 0x01 }; // IN

        byte[] query = new byte[header.length + qName.length + 4];
        System.arraycopy(header, 0, query, 0, header.length);
        System.arraycopy(qName, 0, query, header.length, qName.length);
        System.arraycopy(qType, 0, query, header.length + qName.length, qType.length);
        System.arraycopy(qClass, 0, query, header.length + qName.length + qType.length, qClass.length);

        return query;
    }

    private InetAddress extractAddress(byte[] response) throws Exception {
        ByteArrayInputStream byteStream = new ByteArrayInputStream(response);
        DataInputStream dataStream = new DataInputStream(byteStream);

        // Skip header
        dataStream.skipBytes(12);

        // Skip question section
        skipSection(dataStream, 1);

        // Number of answer RRs
        int answerCount = readUnsignedShort(dataStream);
        System.out.println("Number of Answer RRs: " + answerCount);

        for (int i = 0; i < answerCount; i++) {
            // Answer section
            String name = readName(dataStream);
            int type = readUnsignedShort(dataStream);
            int cls = readUnsignedShort(dataStream);
            int ttl = readInt(dataStream);
            int dataLength = readUnsignedShort(dataStream);

            if (type == 1) { // Type A
                byte[] ipAddressBytes = new byte[4];
                dataStream.readFully(ipAddressBytes);
                return InetAddress.getByAddress(ipAddressBytes);
            } else {
                dataStream.skipBytes(dataLength);
            }
        }
        return null;
    }

    private String extractText(byte[] response) throws Exception {
        ByteArrayInputStream byteStream = new ByteArrayInputStream(response);
        DataInputStream dataStream = new DataInputStream(byteStream);

        // Skip header
        dataStream.skipBytes(12);

        // Skip question section
        skipSection(dataStream, 1);

        // Number of answer RRs
        int answerCount = readUnsignedShort(dataStream);
        System.out.println("Number of Answer RRs: " + answerCount);

        for (int i = 0; i < answerCount; i++) {
            // Answer section
            String name = readName(dataStream);
            int type = readUnsignedShort(dataStream);
            int cls = readUnsignedShort(dataStream);
            int ttl = readInt(dataStream);
            int dataLength = readUnsignedShort(dataStream);

            if (type == 16) { // Type TXT
                byte[] txtData = new byte[dataLength];
                dataStream.readFully(txtData);
                return new String(txtData, "UTF-8");
            } else {
                dataStream.skipBytes(dataLength);
            }
        }
        return null;
    }

    private String extractCNAME(byte[] response) throws Exception {
        ByteArrayInputStream byteStream = new ByteArrayInputStream(response);
        DataInputStream dataStream = new DataInputStream(byteStream);

        // Skip header
        dataStream.skipBytes(12);

        // Skip question section
        skipSection(dataStream, 1);

        // Number of answer RRs
        int answerCount = readUnsignedShort(dataStream);
        System.out.println("Number of Answer RRs: " + answerCount);

        for (int i = 0; i < answerCount; i++) {
            // Answer section
            String name = readName(dataStream);
            int type = readUnsignedShort(dataStream);
            int cls = readUnsignedShort(dataStream);
            int ttl = readInt(dataStream);
            int dataLength = readUnsignedShort(dataStream);

            if (type == 5) { // Type CNAME
                byte[] cnameData = readData(dataStream, dataLength);
                ByteArrayInputStream cnameStream = new ByteArrayInputStream(cnameData);
                DataInputStream cnameDataStream = new DataInputStream(cnameStream);
                String cname = readName(cnameDataStream);
                return cname;
            } else {
                dataStream.skipBytes(dataLength);
            }
        }
        return null;
    }

    private List<String> extractReferral(byte[] response) throws Exception {
        List<String> referrals = new ArrayList<>();
        ByteArrayInputStream byteStream = new ByteArrayInputStream(response);
        DataInputStream dataStream = new DataInputStream(byteStream);

        // Skip header
        dataStream.skipBytes(12);

        // Skip question section
        skipSection(dataStream, 1);

        // Number of answer RRs
        int answerCount = readUnsignedShort(dataStream);
        System.out.println("Number of Answer RRs: " + answerCount);

        // Skip answer section
        skipSection(dataStream, answerCount);

        // Number of authority RRs
        int authorityCount = readUnsignedShort(dataStream);
        System.out.println("Number of Authority RRs: " + authorityCount);
        referrals.addAll(extractRecords(dataStream, authorityCount));

        // Number of additional RRs
        int additionalCount = readUnsignedShort(dataStream);
        System.out.println("Number of Additional RRs: " + additionalCount);
        // Skip additional section
        skipSection(dataStream, additionalCount);

        return referrals;
    }

    private List<String> extractRecords(DataInputStream dataStream, int count) throws IOException {
        List<String> records = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            // Record section
            String name = readName(dataStream);
            int type = readUnsignedShort(dataStream);
            int cls = readUnsignedShort(dataStream);
            int ttl = readInt(dataStream);
            int dataLength = readUnsignedShort(dataStream);

            if (type == 2) { // NS record type
                byte[] nsData = readData(dataStream, dataLength);
                ByteArrayInputStream nsStream = new ByteArrayInputStream(nsData);
                DataInputStream nsDataStream = new DataInputStream(nsStream);
                String ns = readName(nsDataStream);
                records.add("NS: " + ns);
            } else if (type == 5) { // CNAME record type
                byte[] cnameData = readData(dataStream, dataLength);
                ByteArrayInputStream cnameStream = new ByteArrayInputStream(cnameData);
                DataInputStream cnameDataStream = new DataInputStream(cnameStream);
                String cname = readName(cnameDataStream);
                records.add("CNAME: " + cname);
            } else {
                dataStream.skipBytes(dataLength);
            }
        }
        return records;
    }

    private void skipSection(DataInputStream dataStream, int count) throws IOException {
        for (int i = 0; i < count; i++) {
            readName(dataStream); // Name
            dataStream.skipBytes(10); // Type, Class, TTL, and Data Length
        }
    }

    private String readName(DataInputStream dataStream) throws IOException {
        ByteArrayOutputStream nameStream = new ByteArrayOutputStream();
        int length;
        while ((length = dataStream.readUnsignedByte()) != 0) {
            if ((length & 0xC0) == 0xC0) {
                // Pointer
                int offset = ((length & 0x3F) << 8) | dataStream.readUnsignedByte();
                dataStream.skipBytes(offset);
                break;
            } else {
                if (nameStream.size() > 0) {
                    nameStream.write('.');
                }
                byte[] label = new byte[length];
                dataStream.readFully(label);
                nameStream.write(label);
            }
        }
        return nameStream.toString("UTF-8");
    }

    private byte[] readData(DataInputStream dataStream, int length) throws IOException {
        byte[] data = new byte[length];
        dataStream.readFully(data);
        return data;
    }

    private int readUnsignedShort(DataInputStream dataStream) throws IOException {
        return dataStream.readUnsignedShort();
    }

    private int readInt(DataInputStream dataStream) throws IOException {
        return dataStream.readInt();
    }

    public static void main(String[] args) {
        try {
            Resolver r = new Resolver();

            // Use a.root-servers.net.
            byte[] rootServer = new byte[] { -86, -9, -86, 2 };
            r.setNameServer(InetAddress.getByAddress(rootServer), 53);

            // Try to look up some records
            InetAddress i = r.iterativeResolveAddress("moodle4-vip.city.ac.uk.");
            if (i == null) {
                System.out.println("moodle4-vip.city.ac.uk. does have an A record?");
            } else {
                System.out.println("moodle4-vip.city.ac.uk.\tA\t" + i.toString());
            }

            String txt = r.iterativeResolveText("city.ac.uk.");
            if (txt == null) {
                System.out.println("city.ac.uk. does have TXT records?");
            } else {
                System.out.println("city.ac.uk.\tTXT\t" + txt);
            }

            String cn = r.iterativeResolveName("moodle4.city.ac.uk.", 5);
            if (cn == null) {
                System.out.println("moodle4.city.ac.uk. should be a CNAME?");
            } else {
                System.out.println("moodle4.city.ac.uk.\tCNAME\t" + cn);
            }

            // Test referral extraction
            byte[] response = r.queryDNS("example.com.", 1); // Use any domain
            List<String> referrals = r.extractReferral(response);
            System.out.println("Referrals:");
            for (String referral : referrals) {
                System.out.println("\t" + referral);
            }

        } catch (Exception e) {
            System.out.println("Exception caught");
            e.printStackTrace();
        }
    }
}
