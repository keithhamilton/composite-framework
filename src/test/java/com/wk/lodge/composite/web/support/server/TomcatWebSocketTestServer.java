package com.wk.lodge.composite.web.support.server;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.coyote.http11.Http11NioProtocol;
import org.apache.tomcat.util.descriptor.web.ApplicationListener;
import org.apache.tomcat.websocket.server.WsContextListener;
import org.springframework.web.SpringServletContainerInitializer;
import org.springframework.web.WebApplicationInitializer;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;

/**
 * A wrapper around an embedded {@link org.apache.catalina.startup.Tomcat} server
 * for use in testing Spring WebSocket applications.
 *
 * Ensures the Tomcat's WsContextListener is deployed and helps with loading
 * Spring configuration and deploying Spring MVC's DispatcherServlet.
 *
 * @author Rossen Stoyanchev
 */
public class TomcatWebSocketTestServer implements WebSocketTestServer {

	private static final ApplicationListener WS_APPLICATION_LISTENER =
			new ApplicationListener(WsContextListener.class.getName(), false);

	private final Tomcat tomcatServer;

	private final int port;

	private Context context;


	public TomcatWebSocketTestServer(int port) {

		this.port = port;

		Connector connector = new Connector(Http11NioProtocol.class.getName());
        connector.setPort(this.port);

        File baseDir = createTempDir("tomcat");
        String baseDirPath = baseDir.getAbsolutePath();

		this.tomcatServer = new Tomcat();
		this.tomcatServer.setBaseDir(baseDirPath);
		this.tomcatServer.setPort(this.port);
        this.tomcatServer.getService().addConnector(connector);
        this.tomcatServer.setConnector(connector);
	}

	private File createTempDir(String prefix) {
		try {
			File tempFolder = File.createTempFile(prefix + ".", "." + getPort());
			tempFolder.delete();
			tempFolder.mkdir();
			tempFolder.deleteOnExit();
			return tempFolder;
		}
		catch (IOException ex) {
			throw new RuntimeException("Unable to create temp directory", ex);
		}
	}

	public int getPort() {
		return this.port;
	}

	@Override
	public void deployConfig(WebApplicationContext cxt) {
		this.context = this.tomcatServer.addContext("", System.getProperty("java.io.tmpdir"));
		this.context.addApplicationListener(WS_APPLICATION_LISTENER);
		Tomcat.addServlet(context, "dispatcherServlet", new DispatcherServlet(cxt));
		this.context.addServletMapping("/", "dispatcherServlet");
	}

	public void deployConfig(Class<? extends WebApplicationInitializer>... initializers) {

        this.context = this.tomcatServer.addContext("", System.getProperty("java.io.tmpdir"));

		// Add Tomcat's DefaultServlet
		Wrapper defaultServlet = this.context.createWrapper();
		defaultServlet.setName("default");
		defaultServlet.setServletClass("org.apache.catalina.servlets.DefaultServlet");
		this.context.addChild(defaultServlet);

		// Ensure WebSocket support
		this.context.addApplicationListener(WS_APPLICATION_LISTENER);

		this.context.addServletContainerInitializer(
				new SpringServletContainerInitializer(), new HashSet<Class<?>>(Arrays.asList(initializers)));
	}

	public void undeployConfig() {
		if (this.context != null) {
			this.tomcatServer.getHost().removeChild(this.context);
		}
	}

	public void start() throws Exception {
		this.tomcatServer.start();
	}

	public void stop() throws Exception {
		this.tomcatServer.stop();
	}

}
