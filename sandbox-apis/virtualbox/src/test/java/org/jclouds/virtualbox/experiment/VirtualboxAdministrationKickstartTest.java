package org.jclouds.virtualbox.experiment;

import static com.google.common.base.Throwables.propagate;
import static com.google.common.io.ByteStreams.copy;
import static com.google.common.io.Closeables.closeQuietly;
import static org.testng.Assert.assertEquals;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jclouds.compute.domain.ExecResponse;
import org.jclouds.crypto.CryptoStreams;
import org.jclouds.domain.Credentials;
import org.jclouds.logging.log4j.config.Log4JLoggingModule;
import org.jclouds.net.IPSocket;
import org.jclouds.predicates.InetSocketAddressConnect;
import org.jclouds.predicates.RetryablePredicate;
import org.jclouds.ssh.SshClient;
import org.jclouds.ssh.jsch.config.JschSshClientModule;
import org.jclouds.util.Strings2;
import org.jclouds.virtualbox.experiment.settings.KeyboardScancodes;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeGroups;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Optional;
import org.testng.annotations.Test;
import org.virtualbox_4_0.AccessMode;
import org.virtualbox_4_0.DeviceType;
import org.virtualbox_4_0.IMachine;
import org.virtualbox_4_0.IMedium;
import org.virtualbox_4_0.IProgress;
import org.virtualbox_4_0.ISession;
import org.virtualbox_4_0.IStorageController;
import org.virtualbox_4_0.LockType;
import org.virtualbox_4_0.MachineState;
import org.virtualbox_4_0.NATProtocol;
import org.virtualbox_4_0.NetworkAdapterType;
import org.virtualbox_4_0.SessionState;
import org.virtualbox_4_0.StorageBus;
import org.virtualbox_4_0.VirtualBoxManager;
import org.virtualbox_4_0.jaxws.MediumVariant;

import com.google.common.base.Predicate;
import com.google.common.base.Throwables;
import com.google.common.io.Files;
import com.google.common.io.InputSupplier;
import com.google.inject.Guice;
import com.google.inject.Injector;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.util.log.Log;


@Test(groups = "live", testName = "virtualbox.VirtualboxAdministrationKickstartTest")
public class VirtualboxAdministrationKickstartTest {

	protected String provider = "virtualbox";
	protected String identity;
	protected String credential;
	protected String endpoint;
	protected String apiversion;
	protected String vmName;

	VirtualBoxManager manager = VirtualBoxManager.createInstance("");

	protected Injector injector;
	protected Predicate<IPSocket> socketTester;
	protected SshClient.Factory sshFactory;

	protected String settingsFile; // Fully qualified path where the settings
	protected String osTypeId; // Guest OS Type ID.
	protected String vmId; // Machine UUID (optional).
	protected boolean forceOverwrite; 
	protected String osUsername;
	protected String osPassword;
	protected String diskFormat;

	protected String workingDir;
	protected String originalDisk;
	protected String clonedDisk;

	protected String guestAdditionsDvd;
	private String gaIsoUrl;
	private String vboxwebsrvStartCommand;

	private String gaIsoName;
	private String hostUsername;
	private String hostPassword;
	private String installVboxOse;
	private String distroIsoUrl;
	private String distroIsoName;
	private String distroDvd;
	private String controllerIDE;
	private String controllerSATA;
	private String keyboardSequence;
	private String admin_pwd;
	private String vdiName;
	private String preseedUrl;

	protected Server server = null;
	private InetSocketAddress testPort;
	private String vboxManageCommand;

	
	protected void setupCredentials() {
		identity = System.getProperty("test." + provider + ".identity",
				"administrator");
		credential = System.getProperty("test." + provider + ".credential",
				"12345");
		endpoint = System.getProperty("test." + provider + ".endpoint",
				"http://localhost:18083/");
		apiversion = System.getProperty("test." + provider + ".apiversion");
	}

	protected void setupConfigurationProperties() {

		admin_pwd = System.getProperty("test." + provider + ".admin_pwd",
				"password");
		// OS
		osUsername = System.getProperty("test." + provider + ".osusername",
				"toor");
		osPassword = System.getProperty("test." + provider + ".ospassword",
				"password");
		controllerIDE = System.getProperty("test." + provider
				+ ".controllerIde", "IDE Controller");
		controllerSATA = System.getProperty("test." + provider
				+ ".controllerSata", "SATA Controller");
		// Create disk If the @a format attribute is empty or null then the
		// default storage format specified by
		// ISystemProperties#defaultHardDiskFormat
		diskFormat = System.getProperty("test." + provider + ".diskformat", "");

		// VBOX
		settingsFile = null; // Fully qualified path where the settings file
		osTypeId = System.getProperty("test." + provider + ".osTypeId", ""); // Guest
																				// OS
																				// Type
																				// ID.
		vmId = System.getProperty("test." + provider + ".vmId", null); // Machine
																		// UUID
																		// (optional).
		forceOverwrite = true; // If true, an existing machine settings file
								// will be overwritten.
		vmName = System.getProperty("test." + provider + ".vmname",
				"jclouds-virtualbox-kickstart-admin");

		workingDir = System.getProperty("user.home")
				+ File.separator
				+ System.getProperty("test." + provider + ".workingDir",
						"jclouds-virtualbox-test");
		if (new File(workingDir).mkdir());
		vdiName = System.getProperty("test." + provider + ".vdiName",
				"centos-5.2-x86.7z");
		gaIsoName = System.getProperty("test." + provider + ".gaIsoName",
				"VBoxGuestAdditions_4.0.2-update-69551.iso");
		gaIsoUrl = System.getProperty("test." + provider + ".gaIsoUrl",
						"http://download.virtualbox.org/virtualbox/4.0.2/VBoxGuestAdditions_4.0.2-update-69551.iso");

		distroIsoName = System.getProperty("test." + provider
				+ ".distroIsoName", "ubuntu-11.04-server-i386.iso");
		distroIsoUrl = System
				.getProperty("test." + provider + ".distroIsoUrl",
						"http://releases.ubuntu.com/11.04/ubuntu-11.04-server-i386.iso");

		installVboxOse = System.getProperty("test." + provider + ".installvboxose", "sudo -S apt-get --yes install virtualbox-ose");

		originalDisk = workingDir
				+ File.separator
				+ "VDI"
				+ File.separator
				+ System.getProperty("test." + provider + ".originalDisk",
						"centos-5.2-x86.vdi");
		clonedDisk = workingDir
				+ File.separator
				+ System.getProperty("test." + provider + ".clonedDisk",
						"template.vdi");
		guestAdditionsDvd = workingDir
				+ File.separator
				+ System.getProperty("test." + provider + ".guestAdditionsDvd",
						"VBoxGuestAdditions_4.0.2-update-69551.iso");

		distroDvd = workingDir
				+ File.separator
				+ System.getProperty("test." + provider + ".distroDvd",
						distroIsoName);

		preseedUrl = System
				.getProperty(
						"test." + provider + ".preseedurl", "http://dl.dropbox.com/u/693111/preseed.cfg");
		keyboardSequence = System
				.getProperty(
						"test." + provider + ".keyboardSequence",
						"<Esc> <Esc> <Enter> "
								//+ "/install/vmlinuz noapic preseed/url=http://192.168.1.12/src/test/resources/preseed.cfg "
								+ "/install/vmlinuz noapic preseed/url=" + preseedUrl + " "
								+ "debian-installer=en_US auto locale=en_US kbd-chooser/method=us "
								+ "hostname=" + vmName	+ " "
								+ "fb=false debconf/frontend=noninteractive "
								+ "keyboard-configuration/layout=USA keyboard-configuration/variant=USA console-setup/ask_detect=false "
								+ "initrd=/install/initrd.gz -- <Enter>");

		vboxwebsrvStartCommand = System.getProperty("test." + provider
				+ ".vboxwebsrvStartCommand", "/usr/bin/vboxwebsrv.exe");
		vboxManageCommand = System
				.getProperty(
						"test." + provider + ".vboxmanage", "VBoxManage");
		if (!new File(distroDvd).exists()) {
			try {
				downloadFile(distroIsoUrl, workingDir, distroIsoName, null);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		if (!new File(guestAdditionsDvd).exists()) {
			try {
				downloadFile(gaIsoUrl, workingDir, gaIsoName, null);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	@BeforeGroups(groups = "live")
	protected void setupClient() throws IOException, InterruptedException {
		hostUsername = System.getProperty("test." + provider + ".hostusername", "toor");
		hostPassword = System.getProperty("test." + provider + ".hostpassword",
				"password");

		injector = Guice.createInjector(new JschSshClientModule(),
				new Log4JLoggingModule());
		sshFactory = injector.getInstance(SshClient.Factory.class);
		socketTester = new RetryablePredicate<IPSocket>(
				new InetSocketAddressConnect(), 3600, 1, TimeUnit.SECONDS);
		injector.injectMembers(socketTester);

		setupCredentials();
		setupConfigurationProperties();

		installVbox();
		// startup vbox web server
		startupVboxWebServer(vboxwebsrvStartCommand);
		
		// configure jetty HTTP server
		try {
			configureJettyServer();
		} catch (Exception e) {
			propagate(e);
		}
	}

	private void configureJettyServer() throws Exception {
		Server server = new Server(8080);

		ResourceHandler resource_handler = new ResourceHandler();
		resource_handler.setDirectoriesListed(true);
		resource_handler.setWelcomeFiles(new String[]{ "index.html" });

		resource_handler.setResourceBase(".");
		Log.info("serving " + resource_handler.getBaseResource());

		HandlerList handlers = new HandlerList();
		handlers.setHandlers(new Handler[] { resource_handler, new DefaultHandler() });
		server.setHandler(handlers);
	}

	private void installVbox() throws IOException, InterruptedException {
		IPSocket socket = new IPSocket("127.0.0.1", 22);
		socketTester.apply(socket);
		SshClient client = sshFactory.create(socket, new Credentials(
				hostUsername, hostPassword));
		try {
			client.connect();
			client.exec("echo " + hostPassword + " | " + installVboxOse);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (client != null)
				client.disconnect();
		}

	}

	/**
	 * 
	 * @param command
	 *            absolute path to command. For ubuntu 10.04:
	 *            /usr/bin/vboxwebsrv
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private void startupVboxWebServer(String command) throws IOException,
			InterruptedException {
		// Disable login credential: $
		// rt.exec("VBoxManage setproperty websrvauthlibrary null");
		IPSocket socket = new IPSocket("127.0.0.1", 22);
		socketTester.apply(socket);
		SshClient client = sshFactory.create(socket, new Credentials(hostUsername, hostPassword));
		try {
			client.connect();
			ExecResponse response = client.exec("start " + command);
			System.out.println(response.getOutput());
		} catch (Exception e) {
			propagate(e);
		} finally {
			if (client != null)
				client.disconnect();
		}
	}

	@BeforeMethod
	protected void setupManager() {
		manager.connect(endpoint, identity, credential);
	}

	@AfterMethod
	protected void disconnectAndClenaupManager() throws RemoteException,
			MalformedURLException {
		manager.disconnect();
		manager.cleanup();
	}

	public void testCreateVirtualMachine() {
		IMachine newVM = manager.getVBox().createMachine(settingsFile, vmName,
				osTypeId, vmId, forceOverwrite);
		manager.getVBox().registerMachine(newVM);
		assertEquals(newVM.getName(), vmName);
	}

	@Test(dependsOnMethods = "testCreateVirtualMachine")
	public void testChangeRAM() {
		Long memorySize = new Long(1024);
		ISession session = manager.getSessionObject();
		IMachine machine = manager.getVBox().findMachine(vmName);
		machine.lockMachine(session, LockType.Write);
		IMachine mutable = session.getMachine();
		mutable.setMemorySize(memorySize);
		mutable.saveSettings();
		session.unlockMachine();
		assertEquals(manager.getVBox().findMachine(vmName).getMemorySize(),
				memorySize);
	}

	@Test(dependsOnMethods = "testChangeRAM")
	public void testCreateIDEController() {
		ISession session = manager.getSessionObject();
		IMachine machine = manager.getVBox().findMachine(vmName);
		machine.lockMachine(session, LockType.Write);
		IMachine mutable = session.getMachine();
		mutable.addStorageController(controllerIDE, StorageBus.IDE);
		mutable.saveSettings();
		session.unlockMachine();
		assertEquals(manager.getVBox().findMachine(vmName)
				.getStorageControllers().size(), 1);
	}

	@Test(dependsOnMethods = "testCreateIDEController")
	public void testAttachIsoDvd() {
		IMedium distroHD = manager.getVBox().openMedium(distroDvd,
				DeviceType.DVD, AccessMode.ReadOnly);

		ISession session = manager.getSessionObject();
		IMachine machine = manager.getVBox().findMachine(vmName);
		machine.lockMachine(session, LockType.Write);
		IMachine mutable = session.getMachine();
		mutable.attachDevice(controllerIDE, 0, 0, DeviceType.DVD, distroHD);
		mutable.saveSettings(); // write settings to xml
		session.unlockMachine();
		assertEquals(distroHD.getId().equals(""), false);
	}

	@Test(dependsOnMethods = "testAttachIsoDvd")
	public void testCreateScsiController() {
		ISession session = manager.getSessionObject();
		IMachine machine = manager.getVBox().findMachine(vmName);
		machine.lockMachine(session, LockType.Write);
		IMachine mutable = session.getMachine();
		mutable.addStorageController(controllerSATA, StorageBus.SATA);
		mutable.saveSettings();
		session.unlockMachine();
		assertEquals(manager.getVBox().findMachine(vmName)
				.getStorageControllers().size(), 2);
	}

	@Test(dependsOnMethods = "testCreateScsiController")
	public void testCreateAndAttachHardDisk() {
		/*
		 * IMedium IVirtualBox::createHardDisk(String format, String location)
		 * If the format attribute is empty or null then the default
		 * storage format speciï¬�ed by ISystemProperties::defaultHardDiskFormat will be used for creating
		 * a storage unit of the medium.
		 */
		/*
		IMedium hd = manager.getVBox().createHardDisk(null, workingDir + File.separator +  "disk.vdi");
		IProgress progress = hd.createBaseStorage(new Long(8*1024), new Long(MediumVariant.STANDARD.ordinal()));
		*/
		IMedium hd = null;
		if(!new File(clonedDisk).exists()) {
			hd = manager.getVBox().createHardDisk(diskFormat, clonedDisk);
			long size = 120*1024*1024;
			hd.createBaseStorage(new Long(size), new Long(MediumVariant.FIXED.ordinal()));
		} else
			hd = manager.getVBox().openMedium(clonedDisk, DeviceType.HardDisk, AccessMode.ReadWrite);
		ISession session = manager.getSessionObject();
		IMachine machine = manager.getVBox().findMachine(vmName);
		machine.lockMachine(session, LockType.Write);
		IMachine mutable = session.getMachine();
		mutable.attachDevice(controllerSATA, 0, 0, DeviceType.HardDisk, hd);
		mutable.saveSettings(); // write settings to xml
		session.unlockMachine();
		assertEquals(hd.getId().equals(""), false);
	}

	
	  @Test(dependsOnMethods = "testCreateAndAttachHardDisk") 
	  public void testConfigureNIC() { 
		  ISession session = manager.getSessionObject();
	  IMachine machine = manager.getVBox().findMachine(vmName);
	  machine.lockMachine(session, LockType.Write); 
	  IMachine mutable =  session.getMachine(); 

	  
		// network BRIDGED to access HTTP server
		String hostInterface = null;
	    String command = vboxManageCommand + " list bridgedifs";
	    try {
			Process child = Runtime.getRuntime().exec(command);
				        BufferedReader bufferedReader = new BufferedReader(
	                new InputStreamReader(child.getInputStream()));
				        String line = "";
				        boolean found = false;

			while ( (line = bufferedReader.readLine()) != null && !found){

				if(line.split(":")[0].contains("Name") ){
					hostInterface = line.split(":")[1];
				}
				if( line.split(":")[0].contains("Status") && line.split(":")[1].contains("Up") ){
					System.out.println("bridge: " + hostInterface.trim());
					found = true;
				}
			}
			
			// Bridged
			/*
			mutable.getNetworkAdapter(new Long(0)).attachToBridgedInterface();
			mutable.getNetworkAdapter(new Long(0)).setHostInterface(hostInterface.trim());							
			mutable.getNetworkAdapter(new Long(0)).setEnabled(true);
			*/
			
			// NAT			 
			mutable.getNetworkAdapter(new Long(0)).attachToNAT();
			mutable.getNetworkAdapter(new Long(0)).setNATNetwork("");
			machine.getNetworkAdapter(new Long(0)).getNatDriver().addRedirect("guestssh", NATProtocol.TCP, "127.0.0.1", 2222, "", 22);
			mutable.getNetworkAdapter(new Long(0)).setEnabled(true);
			
			mutable.saveSettings(); 
			session.unlockMachine();

			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	 }
	  
	@Test(dependsOnMethods = "testConfigureNIC")
	public void testStartVirtualMachine() {
		IMachine machine = manager.getVBox().findMachine(vmName);
		ISession session = manager.getSessionObject();
		launchVMProcess(machine, session);
		assertEquals(machine.getState(), MachineState.Running);
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		sendKeyboardSequence(keyboardSequence);
	}
	
	@Test(dependsOnMethods = "testStartVirtualMachine")
	public void testConfigureGuestAdditions() {
		
		// configure GA
		IPSocket socket = new IPSocket("127.0.0.1", 2222);
		socketTester.apply(socket);
		SshClient client = sshFactory.create(socket, new Credentials(osUsername, osPassword));
		try {
			client.connect();
			//Configure your system for building kernel modules by running 
			ExecResponse exec = client.exec("echo " + osPassword + " | " + "sudo -S m-a prepare -i");
			System.out.println(exec);
		} finally {
			if (client != null) {
				client.disconnect();
			}
		}


		socketTester.apply(socket);
		client = sshFactory.create(socket, new Credentials(osUsername,
				osPassword));
		try {
			client.connect();
			ExecResponse exec = client
					.exec("echo " + osPassword + " | " + "sudo -S  mount -o loop /usr/share/virtualbox/VBoxGuestAdditions.iso /mnt");
			System.out.println(exec);
			exec = client.exec("echo " + osPassword + " | " + "sudo -S  sh /mnt/VBoxLinuxAdditions.run");
			System.out.println(exec);
		} finally {
			if (client != null)
				client.disconnect();
		}
	}

	@Test(dependsOnMethods = "testConfigureGuestAdditions")
	public void testStopVirtualMachine() {
		IMachine machine = manager.getVBox().findMachine(vmName);
		powerDownMachine(machine);
		assertEquals(machine.getState(), MachineState.PoweredOff);
	}

	/**
	 * @param machine
	 */
	private void powerDownMachine(IMachine machine) {
		try {
			ISession machineSession = manager.openMachineSession(machine);
			IProgress progress = machineSession.getConsole().powerDown();
			progress.waitForCompletion(-1);
			machineSession.unlockMachine();

			while (!machine.getSessionState().equals(SessionState.Unlocked)) {
				try {
					System.out
							.println("waiting for unlocking session - session state: "
									+ machine.getSessionState());
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	@Test(dependsOnMethods = "testStopVirtualMachine")
	public void cleanUp() throws IOException {
		ISession session = manager.getSessionObject();
		IMachine machine = manager.getVBox().findMachine(vmName);
		machine.lockMachine(session, LockType.Write);
		IMachine mutable = session.getMachine();
		mutable.getNetworkAdapter(new Long(0)).getNatDriver()
				.removeRedirect("guestssh");
		// detach disk from controller
		mutable.detachDevice(controllerIDE, 0, 0);
		mutable.saveSettings();
		session.unlockMachine();

		for (IStorageController storageController : machine
				.getStorageControllers()) {
			if (storageController.getName().equals(controllerSATA)) {
				session = manager.getSessionObject();
				machine.lockMachine(session, LockType.Write);

				mutable = session.getMachine();
				mutable.detachDevice(storageController.getName(), 1, 1);
				mutable.saveSettings();
				session.unlockMachine();
			}
		}
	}

	@AfterClass
	void stopVboxWebServer() throws IOException {
		// stop vbox web server
		IPSocket socket = new IPSocket("127.0.0.1", 22);
		socketTester.apply(socket);
		SshClient client = sshFactory.create(socket, new Credentials(
				hostUsername, hostPassword));
		try {
			client.connect();
			ExecResponse exec = client.exec("pidof vboxwebsrv | xargs kill");
			System.out.println(exec.getOutput());

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (client != null)
				client.disconnect();
		}
	}

	/**
	 * 
	 * @param workingDir
	 * @param vdiUrl
	 * @param proxy
	 *            Proxy proxy , new Proxy(Proxy.Type.HTTP, new
	 *            InetSocketAddress("localhost", 5865));
	 * @return
	 * @throws Exception
	 */
	private File downloadFile(String sourceURL, String destinationDir,
			String vboxGuestAdditionsName, Proxy proxy) throws Exception {

		String absolutePathName = destinationDir + File.separator
				+ vboxGuestAdditionsName;
		File iso = new File(absolutePathName);

		final URL isoURL = new URL(sourceURL);
		final HttpURLConnection uc = (HttpURLConnection) isoURL
				.openConnection(); // isoURL.openConnection(proxy);
		uc.connect();
		if (!iso.exists()) {
			System.out.println("Start download " + sourceURL + " to "
					+ absolutePathName);
			Files.copy(new InputSupplier<InputStream>() {

				@Override
				public InputStream getInput() throws IOException {
					return uc.getInputStream();
				}

			}, iso);
		}
		return iso;
	}
	
	private void sendKeyboardSequence(String keyboardSequence) {
		String[] sequenceSplited = keyboardSequence.split(" ");
		/*
		for (String word : sequenceSplited) {
			
			String converted = stringToKeycode(word);
			for (String string : converted.split("  ")) {
				try {
					Thread.sleep(100);
					Runtime.getRuntime().exec(vboxManageCommand + " controlvm " + vmName + " keyboardputscancode " + string);
					Thread.sleep(50);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}	
		}
		*/
		
		for (String word : sequenceSplited) {
				IPSocket socket = new IPSocket("127.0.0.1", 22);
			socketTester.apply(socket);
			SshClient client = sshFactory.create(socket, new Credentials(hostUsername, hostPassword));
			try {
				client.connect();
				String converted = stringToKeycode(word);
				for (String string : converted.split("  ")) {
					ExecResponse response = client.exec(vboxManageCommand + " controlvm " + vmName + " keyboardputscancode " + string);
					System.out.println(response.getOutput());
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				if (client != null)
					client.disconnect();
			}	
		}
	
	}

	private String stringToKeycode(String s) {

		StringBuilder keycodes = new StringBuilder();
		for (String specialButton : KeyboardScancodes.SPECIAL_KEYBOARD_BUTTON_MAP.keySet()) {
			if(s.startsWith(specialButton)) {
				keycodes.append(KeyboardScancodes.SPECIAL_KEYBOARD_BUTTON_MAP.get(specialButton));
				return keycodes.toString();
			}
		}
        
		int i = 0, j = 0;
        while(i < s.length()) {
        	String digit = s.substring(i, i+1);
        	String hex = KeyboardScancodes.NORMAL_KEYBOARD_BUTTON_MAP.get(digit);
        	keycodes.append(hex + "  ");
        	i++;
        }
        keycodes.append(KeyboardScancodes.SPECIAL_KEYBOARD_BUTTON_MAP.get("<Spacebar>") + " ");

        return keycodes.toString();
		}

	
	/**
	 * 
	 * @param machine
	 * @param session
	 */
	private void launchVMProcess(IMachine machine, ISession session) {
		IProgress prog = machine.launchVMProcess(session, "gui", "");
		prog.waitForCompletion(-1);
		session.unlockMachine();
	}
}