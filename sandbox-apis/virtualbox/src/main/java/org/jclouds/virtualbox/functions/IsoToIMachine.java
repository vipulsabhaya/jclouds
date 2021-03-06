/*
 * *
 *  * Licensed to jclouds, Inc. (jclouds) under one or more
 *  * contributor license agreements.  See the NOTICE file
 *  * distributed with this work for additional information
 *  * regarding copyright ownership.  jclouds licenses this file
 *  * to you under the Apache License, Version 2.0 (the
 *  * "License"); you may not use this file except in compliance
 *  * with the License.  You may obtain a copy of the License at
 *  *
 *  *   http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing,
 *  * software distributed under the License is distributed on an
 *  * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  * KIND, either express or implied.  See the License for the
 *  * specific language governing permissions and limitations
 *  * under the License.
 *
 */

package org.jclouds.virtualbox.functions;

import static com.google.common.base.Throwables.propagate;
import static org.jclouds.compute.options.RunScriptOptions.Builder.runAsRoot;
import static org.jclouds.compute.options.RunScriptOptions.Builder.wrapInInitScript;
import static org.jclouds.virtualbox.config.VirtualBoxConstants.VIRTUALBOX_INSTALLATION_KEY_SEQUENCE;
import static org.jclouds.virtualbox.config.VirtualBoxConstants.VIRTUALBOX_WORKINGDIR;
import static org.virtualbox_4_1.AccessMode.ReadOnly;
import static org.virtualbox_4_1.DeviceType.DVD;
import static org.virtualbox_4_1.LockType.Write;
import static org.virtualbox_4_1.NATProtocol.TCP;
import static org.virtualbox_4_1.NetworkAttachmentType.NAT;

import java.io.File;

import javax.annotation.Resource;
import javax.inject.Named;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.domain.ExecResponse;
import org.jclouds.compute.options.RunScriptOptions;
import org.jclouds.compute.reference.ComputeServiceConstants;
import org.jclouds.domain.Credentials;
import org.jclouds.javax.annotation.Nullable;
import org.jclouds.logging.Logger;
import org.jclouds.ssh.SshException;
import org.jclouds.virtualbox.config.VirtualBoxConstants;
import org.jclouds.virtualbox.functions.admin.StartJettyIfNotAlreadyRunning;
import org.jclouds.virtualbox.settings.KeyboardScancodes;
import org.virtualbox_4_1.AccessMode;
import org.virtualbox_4_1.DeviceType;
import org.virtualbox_4_1.IMachine;
import org.virtualbox_4_1.IMedium;
import org.virtualbox_4_1.IProgress;
import org.virtualbox_4_1.ISession;
import org.virtualbox_4_1.LockType;
import org.virtualbox_4_1.StorageBus;
import org.virtualbox_4_1.VirtualBoxManager;
import org.virtualbox_4_1.jaxws.MediumVariant;

import com.google.common.base.Function;
import com.google.inject.Inject;


public class IsoToIMachine implements Function<String, IMachine> {

	@Resource
	@Named(ComputeServiceConstants.COMPUTE_LOGGER)
	protected Logger logger = Logger.NULL;

	private VirtualBoxManager manager;
	private String adminDisk;
	private String diskFormat;
	private String settingsFile;
	private String vmName;
	private String osTypeId;
	private String vmId;
	private String controllerIDE;
	private boolean forceOverwrite;
	private ComputeServiceContext context;
	private String hostId;
	private String guestId;
	private Credentials credentials;

	@Inject
	public IsoToIMachine(VirtualBoxManager manager,
			String adminDisk,
			String diskFormat,
			String settingsFile,
			String vmName,
			String osTypeId,
			String vmId,
			boolean forceOverwrite,
			String controllerIDE,
			ComputeServiceContext context,
			String hostId,
			String guestId,
			Credentials credentials) {
		super();
		this.manager = manager;
		this.adminDisk = adminDisk;
		this.diskFormat = diskFormat;
		this.settingsFile = settingsFile;
		this.vmName = vmName;
		this.osTypeId = osTypeId;
		this.vmId = vmId;
		this.controllerIDE = controllerIDE;
		this.forceOverwrite = forceOverwrite;
		this.context = context;
		this.hostId = hostId;
		this.guestId = guestId;
		this.credentials = credentials;
	}

	@Override
	public IMachine apply(@Nullable String isoName) {

		String port = System.getProperty(VirtualBoxConstants.VIRTUALBOX_JETTY_PORT, "8080");
		String basebaseResource = ".";
		Server server = new StartJettyIfNotAlreadyRunning(port).apply(basebaseResource);
		
		IMachine vm = manager.getVBox().createMachine(settingsFile, vmName, osTypeId, vmId, forceOverwrite);
		manager.getVBox().registerMachine(vm);

		String defaultWorkingDir = System.getProperty("user.home") + "/jclouds-virtualbox-test";
		String workingDir = System.getProperty(VIRTUALBOX_WORKINGDIR, defaultWorkingDir);
		IMedium distroMedium = manager.getVBox().openMedium(workingDir + "/" + isoName, DVD, ReadOnly, forceOverwrite);

		// Change RAM
		Long memorySize = new Long(1024);
		ISession session = manager.getSessionObject();
		IMachine machine = manager.getVBox().findMachine(vmName);
		machine.lockMachine(session, Write);
		IMachine mutable = session.getMachine();
		mutable.setMemorySize(memorySize);
		mutable.saveSettings();
		session.unlockMachine();

		//  IDE Controller
		machine.lockMachine(session, Write);
		mutable = session.getMachine();
		mutable.addStorageController(controllerIDE, StorageBus.IDE);
		mutable.saveSettings();
		session.unlockMachine();

		// DISK
		String adminDiskPath = workingDir + "/" + adminDisk;
		if (new File(adminDiskPath).exists()) {
			new File(adminDiskPath).delete();
		}
		IMedium hd = manager.getVBox().createHardDisk(diskFormat, adminDiskPath);
		long size = 4L * 1024L * 1024L * 1024L - 4L;
		IProgress storageCreation = hd.createBaseStorage(size, (long) MediumVariant.STANDARD.ordinal());
		storageCreation.waitForCompletion(-1);

		machine.lockMachine(session, Write);
		mutable = session.getMachine();
		mutable.attachDevice(controllerIDE, 0, 0, DeviceType.DVD, distroMedium);
		mutable.saveSettings();
		session.unlockMachine();

		// Create and attach hard disk
		machine.lockMachine(session, Write);
		mutable = session.getMachine();
		mutable.attachDevice(controllerIDE, 0, 1, DeviceType.HardDisk, hd);
		mutable.saveSettings();
		session.unlockMachine();

		// NIC
		machine.lockMachine(session, Write);
		mutable = session.getMachine();

		// NAT
		mutable.getNetworkAdapter(0l).setAttachmentType(NAT);
		machine.getNetworkAdapter(0l)
		.getNatDriver()
		.addRedirect("guestssh", TCP, "127.0.0.1", 2222, "", 22);
		mutable.getNetworkAdapter(0l).setEnabled(true);
		mutable.saveSettings();
		session.unlockMachine();

		String guestAdditionsDvd = workingDir + "/VBoxGuestAdditions_4.1.2.iso";
		IMedium guestAdditionsDvdMedium = manager.getVBox().openMedium(guestAdditionsDvd, DeviceType.DVD, AccessMode.ReadOnly, forceOverwrite);
		machine.lockMachine(session, Write);
		mutable = session.getMachine();
		mutable.attachDevice(controllerIDE, 1, 1, DeviceType.DVD, guestAdditionsDvdMedium);
		mutable.saveSettings();
		session.unlockMachine();

		IProgress prog = machine.launchVMProcess(session, "gui", "");
		prog.waitForCompletion(-1);
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			propagate(e);
		}

		String installKeySequence = System.getProperty(VIRTUALBOX_INSTALLATION_KEY_SEQUENCE, defaultInstallSequence());
		sendKeyboardSequence(installKeySequence);
		session.unlockMachine();

		boolean sshDeamonIsRunning = false;
		while (!sshDeamonIsRunning) {
			try {
				if (runScriptOnNode(guestId, "id", wrapInInitScript(false)).getExitCode() == 0) {
					logger.debug("Got response from ssh daemon.");
					sshDeamonIsRunning = true;
				}
			} catch (SshException e) {
				logger.debug("No response from ssh daemon...");
			}
		}

		logger.debug("Installation of image complete. Powering down...");

		machine.lockMachine(session, LockType.Shared);
		IProgress powerDownProgress = session.getConsole().powerDown();
		powerDownProgress.waitForCompletion(-1);
		session.unlockMachine();

		try {
			logger.debug("Stopping Jetty server...");
			server.stop();
			logger.debug("Jetty server stopped.");
		} catch (Exception e) {
			logger.error(e, "Could not stop Jetty server.");
		}
		return vm;
	}

	private String defaultInstallSequence() {
		return "<Esc><Esc><Enter> "
				+ "/install/vmlinuz noapic preseed/url=http://10.0.2.2:8080/src/test/resources/preseed.cfg "
				+ "debian-installer=en_US auto locale=en_US kbd-chooser/method=us "
				+ "hostname="
				+ vmName
				+ " "
				+ "fb=false debconf/frontend=noninteractive "
				+ "keyboard-configuration/layout=USA keyboard-configuration/variant=USA console-setup/ask_detect=false "
				+ "initrd=/install/initrd.gz -- <Enter>";
	}

	private void sendKeyboardSequence(String keyboardSequence) {
		String[] sequenceSplited = keyboardSequence.split(" ");
		StringBuilder sb = new StringBuilder();
		for (String line : sequenceSplited) {
			String converted = stringToKeycode(line);
			for (String word : converted.split("  ")) {
				sb.append("vboxmanage controlvm " + vmName
						+ " keyboardputscancode " + word + "; ");
				if (word.endsWith(KeyboardScancodes.SPECIAL_KEYBOARD_BUTTON_MAP.get("<Enter>"))) {
					runScriptOnNode(hostId, sb.toString(), runAsRoot(false).wrapInInitScript(false));
					sb.delete(0, sb.length() - 1);
				}
				if (word.endsWith(KeyboardScancodes.SPECIAL_KEYBOARD_BUTTON_MAP.get("<Return>"))) {
					runScriptOnNode(hostId, sb.toString(), runAsRoot(false).wrapInInitScript(false));
					sb.delete(0, sb.length() - 1);
				}

			}
		}
	}

	private String stringToKeycode(String s) {
		StringBuilder keycodes = new StringBuilder();
		if (s.startsWith("<")) {
			String[] specials = s.split("<");
			for (int i = 1; i < specials.length; i++) {
				keycodes.append(KeyboardScancodes.SPECIAL_KEYBOARD_BUTTON_MAP
						.get("<" + specials[i]) + "  ");
			}
			return keycodes.toString();
		}

		int i = 0;
		while (i < s.length()) {
			String digit = s.substring(i, i + 1);
			String hex = KeyboardScancodes.NORMAL_KEYBOARD_BUTTON_MAP
					.get(digit);
			keycodes.append(hex + " ");
			if (i != 0 && i % 14 == 0)
				keycodes.append(" ");
			i++;
		}
		keycodes.append(KeyboardScancodes.SPECIAL_KEYBOARD_BUTTON_MAP
				.get("<Spacebar>") + " ");

		return keycodes.toString();
	}

	protected ExecResponse runScriptOnNode(String nodeId, String command, RunScriptOptions options) {
		return context.getComputeService().runScriptOnNode(nodeId, command, options);
	}

}
