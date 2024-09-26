import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class DNSReferral {
    
    public static void main(String[] args) throws Exception {
        String domain = "moodle4-vip.city.ac.uk";
        InetAddress server = InetAddress.getByName("170.247.170.2");
        
        byte[] request = createDNSRequest(domain);
        DatagramSocket socket = new DatagramSocket();
        DatagramPacket packet = new DatagramPacket(request, request.length, server, 53);
        socket.send(packet);
        
        byte[] response = new byte[512];
        packet = new DatagramPacket(response, response.length);
        socket.receive(packet);
        
        printDNSResponse(response);
        socket.close();
    }
    
    private static byte[] createDNSRequest(String domain) {
        // Create DNS query for 'A' record
        // Your code to construct DNS query goes here
        return new byte[0]; // Replace with actual query bytes
    }
    
    private static void printDNSResponse(byte[] response) throws Exception {
        ByteArrayInputStream bais = new ByteArrayInputStream(response);
        DataInputStream dis = new DataInputStream(bais);
        
        // Skip the header (12 bytes)
        dis.skipBytes(12);
        
        // Read number of Authority RRs
        int authorityRRs = dis.readUnsignedShort();
        
        System.out.println("Number of Authority RRs: " + authorityRRs);
        
        // Process Authority RRs if any
        for (int i = 0; i < authorityRRs; i++) {
            // Read and print Authority RRs (nameservers)
            String nsName = readName(dis);
            int type = dis.readUnsignedShort();
            int classType = dis.readUnsignedShort();
            int ttl = dis.readInt();
            int rdLength = dis.readUnsignedShort();
            byte[] rdata = new byte[rdLength];
            dis.readFully(rdata);
            
            System.out.println("Nameserver: " + nsName);
        }
    }
    
    private static String readName(DataInputStream dis) throws Exception {
        StringBuilder name = new StringBuilder();
        int length;
        while ((length = dis.readUnsignedByte()) > 0) {
            byte[] label = new byte[length];
            dis.readFully(label);
            if (name.length() > 0) {
                name.append(".");
            }
            name.append(new String(label));
        }
        return name.toString();
    }
}
