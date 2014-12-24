package gr.xe.dc.tomcat.listeners;

import java.io.FileWriter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import org.apache.catalina.Container;
import org.apache.catalina.Executor;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Server;
import org.apache.catalina.Service;
import org.apache.catalina.connector.Connector;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

public class InitHealthCheckListener implements
		org.apache.catalina.LifecycleListener {
	private static final Log log = LogFactory
			.getLog(InitHealthCheckListener.class);

	private boolean shutdownOnFailure = true;
	private boolean startupCompleted = false;

	public boolean isShutdownOnFailure() {
		return shutdownOnFailure;
	}

	public void setShutdownOnFailure(boolean shutdownOnFailure) {
		this.shutdownOnFailure = shutdownOnFailure;
	}

	private String notifyFIFO = null;

	public String getNotifyFIFO() {
		return notifyFIFO;
	}

	public void setNotifyFIFO(String notifyFIFO) {
		this.notifyFIFO = notifyFIFO;
	}

	private String notifyAddress = "localhost";

	public String getNotifyAddress() {
		return notifyAddress;
	}

	public void setNotifyAddress(String notifyAddress) {
		this.notifyAddress = notifyAddress;
	}

	private Integer notifyPort = null;

	public Integer getNotifyPort() {
		return notifyPort;
	}

	public void setNotifyPort(Integer notifyPort) {
		this.notifyPort = notifyPort;
	}

	private boolean checkState(final Lifecycle lifecycle) {
		if (lifecycle.getState() == LifecycleState.STARTED)
			return true;

		log.fatal("Component " + lifecycle + " failed to start!");
		return false;
	}

	private boolean checkContainer(final Container container) {
		boolean succeeded = checkState(container);

		for (Container child : container.findChildren())
			succeeded &= checkContainer(child);

		return succeeded;
	}

	private boolean checkService(final Service service) {
		boolean succeeded = checkState(service);

		for (Connector connector : service.findConnectors())
			succeeded &= checkState(connector);

		for (Executor executor : service.findExecutors())
			succeeded &= checkState(executor);

		succeeded &= checkContainer(service.getContainer());

		return succeeded;
	}

	private boolean checkServer(final Server server) {
		boolean succeeded = checkState(server);

		for (Service service : server.findServices())
			succeeded &= checkService(service);

		return succeeded;
	}

	public void lifecycleEvent(LifecycleEvent event) {
		if (!(event.getLifecycle() instanceof Server))
			return;

		if (!startupCompleted && Lifecycle.AFTER_START_EVENT.equals(event.getType()))
			handleEvent((Server) event.getLifecycle(), "startup");
		else if (startupCompleted) {
			if(Lifecycle.BEFORE_STOP_EVENT.equals(event.getType()))
				handleEvent((Server) event.getLifecycle(), "prestop");
			else if(Lifecycle.STOP_EVENT.equals(event.getType()))
				handleEvent((Server) event.getLifecycle(), "stop");
			else if(Lifecycle.AFTER_STOP_EVENT.equals(event.getType()))
				handleEvent((Server) event.getLifecycle(), "poststop");
		}
		return;
	}

	private void handleEvent(Server server, String name) {
		log.info("Server " + name + " event received. Checking component health..");

		final boolean succeeded = checkServer(server);

		if (!startupCompleted) {
			if (succeeded)
				log.info("Server health OK!");
			else
				log.fatal("Initialization failure detected!");
		} else {
			if(!succeeded) {
				log.info("Shutdown detected!");
			} else {
				log.info("No change, server still appears okay.");
				return;
			}
		}

		if (notifyFIFO != null) {
			Thread thread = new Thread(new Runnable() {
				public void run() {
					FileWriter fileWriter = null;

					try {
						fileWriter = new FileWriter(notifyFIFO);

						if (succeeded)
							fileWriter.write("0\n");
						else
							fileWriter.write("1\n");
					} catch (IOException e) {
						log.error("Unable to write status to " + notifyFIFO
								+ ": ", e);
					} finally {
						if (fileWriter == null)
							return;

						try {
							fileWriter.close();
						} catch (IOException e) {
							log.error("Unable to close " + notifyFIFO + ": ", e);
						}
					}
				}
			}, "init-health-check-fifo-writer");

			thread.setDaemon(true);
			thread.start();
		}

		if (notifyPort == null && server.getPort() > 0)
			notifyPort = server.getPort();

		if (notifyPort != null) {
			try {
				InetAddress address = InetAddress.getByName(notifyAddress);

				DatagramSocket socket = null;
				try {
					socket = new DatagramSocket();

					byte[] status = null;
					if (succeeded)
						status = "0\n".getBytes();
					else
						status = "1\n".getBytes();

					socket.send(new DatagramPacket(status, status.length,
							address, notifyPort));
				} catch (SocketException e2) {
					log.error("Unable to create datagram socket: ", e2);
				} catch (IOException e2) {
					log.error("Unable to send datagram packet to "
							+ notifyAddress + ":" + notifyPort + ": ", e2);
				} finally {
					if (socket != null)
						socket.close();
				}
			} catch (UnknownHostException e1) {
				log.error("Unable to resolve " + notifyAddress + ": ", e1);
			}
		}

		if (!succeeded && shutdownOnFailure && !startupCompleted) {
			log.fatal("Shutting down server!");

			try {
				server.stop();
			} catch (LifecycleException e) {
				log.fatal("Stop command failed: ", e);
			}

			try {
				server.destroy();
			} catch (LifecycleException e) {
				log.fatal("Destroy command failed: ", e);
			}
		} else if(succeeded && !startupCompleted)
			startupCompleted = true;
	}
}
