// IN2011 Computer Networks
// Coursework 2023/2024 Resit
//
// This is an example of how the StubResolver can be used.
// It should work with your submission without any changes (as long as
// the name server is accessible from your network).
// This should help you start testing.
// You will need to do more testing than just this.

// DO NOT EDIT starts
import java.net.InetAddress;

public class TestStubResolver {

	public static void main(String[] args) {
		try {
			StubResolver r = new StubResolver();

			// Set the Cloudflare public DNS name server
			byte[] cloudflarePublic = new byte[] { 1, 1, 1, 1 };
			r.setNameServer(InetAddress.getByAddress(cloudflarePublic), 53);

			// Try to look up some records
			InetAddress i = r.recursiveResolveAddress("moodle4-vip.city.ac.uk.");
			if (i == null) {
				System.out.println("moodle4-vip.city.ac.uk. does have an A record?");
			} else {
				System.out.println("moodle4-vip.city.ac.uk.\tA\t" + i.toString());
			}

			String txt = r.recursiveResolveText("city.ac.uk.");
			if (txt == null) {
				System.out.println("city.ac.uk. does have TXT records?");
			} else {
				System.out.println("moodle4-vip.city.ac.uk.\tTXT\t" + txt);
			}

			String cn = r.recursiveResolveName("moodle4.city.ac.uk.", 5);
			if (cn == null) {
				System.out.println("moodle4.city.ac.uk. should be a CNAME?");
			} else {
				System.out.println("moodle4.city.ac.uk.\tCNAME\t" + cn);
			}

		} catch (Exception e) {
			System.out.println("Exception caught");
			e.printStackTrace();
		}

		return;
	}
}
// DO NOT EDIT ends
