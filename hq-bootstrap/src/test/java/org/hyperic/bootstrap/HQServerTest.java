package org.hyperic.bootstrap;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.easymock.EasyMock;
import org.hyperic.sigar.OperatingSystem;
import org.hyperic.sigar.SigarException;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test of {@link HQServer}
 * @author jhickey
 * 
 */
public class HQServerTest {

    private HQServer server;
    private ServerConfigurator serverConfigurator;
    private ProcessManager processManager;
    private EngineController engineController;
    private EmbeddedDatabaseController embeddedDatabaseController;
    private String serverHome = "/Applications/HQ5/server-5.0.0";
    private OperatingSystem osInfo;

    @Before
    public void setUp() {
        this.serverConfigurator = EasyMock.createMock(ServerConfigurator.class);
        this.processManager = EasyMock.createMock(ProcessManager.class);
        this.engineController = EasyMock.createMock(EngineController.class);
        this.embeddedDatabaseController = EasyMock.createMock(EmbeddedDatabaseController.class);
        this.osInfo = org.easymock.classextension.EasyMock.createMock(OperatingSystem.class);
        this.server = new HQServer(serverHome, processManager, embeddedDatabaseController,
            serverConfigurator, engineController, osInfo);
    }

    @Test
    public void testGetJavaOptsSunJava64() {
        Properties testProps = new Properties();
        testProps.put("server.java.opts",
            "-XX:MaxPermSize=192m -Xmx512m -Xms512m -XX:+HeapDumpOnOutOfMemoryError");
        final List<String> expectedOpts = new ArrayList<String>();
        expectedOpts.add("-XX:MaxPermSize=192m");
        expectedOpts.add("-Xmx512m");
        expectedOpts.add("-Xms512m");
        expectedOpts.add("-XX:+HeapDumpOnOutOfMemoryError");
        expectedOpts.add("-Dserver.home=" + serverHome);
        expectedOpts.add("-d64");
        EasyMock.expect(serverConfigurator.getServerProps()).andReturn(testProps);
        org.easymock.classextension.EasyMock.expect(osInfo.getName()).andReturn("SunOS");
        org.easymock.classextension.EasyMock.expect(osInfo.getArch()).andReturn(
            "64-bit something or other");
        replay();
        List<String> javaOpts = server.getJavaOpts();
        verify();
        assertEquals(expectedOpts, javaOpts);
    }

    @Test
    public void testStart() throws Exception {
        EasyMock.expect(engineController.isEngineRunning()).andReturn(false);
        serverConfigurator.configure();
        EasyMock.expect(embeddedDatabaseController.shouldUse()).andReturn(true);
        EasyMock.expect(embeddedDatabaseController.startBuiltInDB()).andReturn(true);
        EasyMock.expect(
            processManager.executeProcess(EasyMock
                .aryEq(new String[] { "java",
                                     "-cp",
                                     serverHome + "/lib/ant-launcher.jar",
                                     "-Dserver.home=" + serverHome,
                                     "-Dant.home=" + serverHome,
                                     "org.apache.tools.ant.launch.Launcher",
                                     "-q",
                                     "-lib",
                                     serverHome + "/lib",
                                     "-logger",
                                     "org.hyperic.tools.ant.installer.InstallerLogger",
                                     "-buildfile",
                                     serverHome + "/data/db-upgrade.xml",
                                     "upgrade" }), EasyMock.eq(serverHome), EasyMock.eq(true),
                EasyMock.eq(HQServer.DB_UPGRADE_PROCESS_TIMEOUT))).andReturn(0);
        Properties testProps = new Properties();
        testProps.put("server.java.opts",
            "-XX:MaxPermSize=192m -Xmx512m -Xms512m -XX:+HeapDumpOnOutOfMemoryError");
        testProps.put("server.webapp.port", "7080");
        final List<String> expectedOpts = new ArrayList<String>();
        expectedOpts.add("-XX:MaxPermSize=192m");
        expectedOpts.add("-Xmx512m");
        expectedOpts.add("-Xms512m");
        expectedOpts.add("-XX:+HeapDumpOnOutOfMemoryError");
        expectedOpts.add("-Dserver.home=" + serverHome);
        EasyMock.expect(serverConfigurator.getServerProps()).andReturn(testProps);
        org.easymock.classextension.EasyMock.expect(osInfo.getName()).andReturn("Mac OS X");
        EasyMock.expect(engineController.start(expectedOpts)).andReturn(0);
        replay();
        server.start();
        verify();
    }

    @Test
    public void testStartServerAlreadyRunning() throws SigarException {
        EasyMock.expect(engineController.isEngineRunning()).andReturn(true);
        replay();
        server.start();
        verify();
    }

    @Test
    public void testStartServerUnableToTellIfRunning() throws SigarException {
        EasyMock.expect(engineController.isEngineRunning()).andThrow(new SigarException());
        replay();
        server.start();
        verify();
    }

    @Test
    public void testStartErrorConfiguring() throws Exception {
        EasyMock.expect(engineController.isEngineRunning()).andReturn(false);
        serverConfigurator.configure();
        EasyMock.expectLastCall().andThrow(new NullPointerException());
        EasyMock.expect(embeddedDatabaseController.shouldUse()).andReturn(false);
        EasyMock.expect(
            processManager.executeProcess(EasyMock
                .aryEq(new String[] { "java",
                                     "-cp",
                                     serverHome + "/lib/ant-launcher.jar",
                                     "-Dserver.home=" + serverHome,
                                     "-Dant.home=" + serverHome,
                                     "org.apache.tools.ant.launch.Launcher",
                                     "-q",
                                     "-lib",
                                     serverHome + "/lib",
                                     "-logger",
                                     "org.hyperic.tools.ant.installer.InstallerLogger",
                                     "-buildfile",
                                     serverHome + "/data/db-upgrade.xml",
                                     "upgrade" }), EasyMock.eq(serverHome), EasyMock.eq(true),
                EasyMock.eq(HQServer.DB_UPGRADE_PROCESS_TIMEOUT))).andReturn(0);
        Properties testProps = new Properties();
        testProps.put("server.java.opts",
            "-XX:MaxPermSize=192m -Xmx512m -Xms512m -XX:+HeapDumpOnOutOfMemoryError");
        testProps.put("server.webapp.port", "7080");
        final List<String> expectedOpts = new ArrayList<String>();
        expectedOpts.add("-XX:MaxPermSize=192m");
        expectedOpts.add("-Xmx512m");
        expectedOpts.add("-Xms512m");
        expectedOpts.add("-XX:+HeapDumpOnOutOfMemoryError");
        expectedOpts.add("-Dserver.home=" + serverHome);
        EasyMock.expect(serverConfigurator.getServerProps()).andReturn(testProps);
        org.easymock.classextension.EasyMock.expect(osInfo.getName()).andReturn("Mac OS X");
        EasyMock.expect(engineController.start(expectedOpts)).andReturn(0);
        replay();
        server.start();
        verify();
    }

    @Test
    public void testStartErrorStartingDB() throws Exception {
        EasyMock.expect(engineController.isEngineRunning()).andReturn(false);
        serverConfigurator.configure();
        EasyMock.expect(embeddedDatabaseController.shouldUse()).andReturn(true);
        EasyMock.expect(embeddedDatabaseController.startBuiltInDB()).andThrow(
            new NullPointerException());
        replay();
        server.start();
        verify();
    }

    @Test
    public void testStartStartDBFailed() throws Exception {
        EasyMock.expect(engineController.isEngineRunning()).andReturn(false);
        serverConfigurator.configure();
        EasyMock.expect(embeddedDatabaseController.shouldUse()).andReturn(true);
        EasyMock.expect(embeddedDatabaseController.startBuiltInDB()).andReturn(false);
        replay();
        server.start();
        verify();
    }

    @Test
    public void testStop() throws Exception {
        EasyMock.expect(engineController.stop()).andReturn(true);
        EasyMock.expect(embeddedDatabaseController.shouldUse()).andReturn(true);
        EasyMock.expect(embeddedDatabaseController.stopBuiltInDB()).andReturn(true);
        replay();
        server.stop();
        verify();
    }

    @Test
    public void testStopErrorStoppingEngine() throws Exception {
        EasyMock.expect(engineController.stop()).andThrow(new SigarException());
        replay();
        server.stop();
        verify();
    }

    @Test
    public void testStopErrorStoppingBuiltInDB() throws Exception {
        EasyMock.expect(engineController.stop()).andReturn(true);
        EasyMock.expect(embeddedDatabaseController.shouldUse()).andReturn(true);
        EasyMock.expect(embeddedDatabaseController.stopBuiltInDB()).andThrow(new SigarException());
        replay();
        server.stop();
        verify();
    }

    private void replay() {
        EasyMock.replay(engineController, serverConfigurator, processManager,
            embeddedDatabaseController);
        org.easymock.classextension.EasyMock.replay(osInfo);
    }

    private void verify() {
        EasyMock.verify(engineController, serverConfigurator, processManager,
            embeddedDatabaseController);
        org.easymock.classextension.EasyMock.verify(osInfo);
    }

}