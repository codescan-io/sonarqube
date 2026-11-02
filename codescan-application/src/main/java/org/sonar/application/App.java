/*
 * SonarQube
 * Copyright (C) 2009-2024 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.application;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import org.apache.commons.lang3.StringUtils;
import org.sonar.api.utils.log.Loggers;
import org.sonar.application.command.CommandFactory;
import org.sonar.application.command.CommandFactoryImpl;
import org.sonar.application.config.AppSettings;
import org.sonar.application.config.AppSettingsLoader;
import org.sonar.application.config.AppSettingsLoaderImpl;
import org.sonar.core.extension.ServiceLoaderWrapper;
import org.sonar.process.System2;
import org.sonar.process.SystemExit;

import static org.sonar.application.config.SonarQubeVersionHelper.getSonarqubeVersion;
import static org.sonar.process.ProcessProperties.Property.CLUSTER_NAME;

public class App {

    /** The one variable the deployment sets for both this server and the CodeScan API. */
    static final String TRUSTED_PROXIES_ENV_VAR = "CODESCAN_TRUSTED_PROXIES";
    private static final String TRUSTED_PROXIES_PROPERTY = "sonar.web.trustedProxiesRegex";

    private final SystemExit systemExit = new SystemExit();
    private StopRequestWatcher stopRequestWatcher = null;
    private StopRequestWatcher hardStopRequestWatcher = null;

    public void start(String[] cliArguments) throws IOException {
        createDefaultSonarPropertiesIfRequired();

        AppSettingsLoader settingsLoader = new AppSettingsLoaderImpl(System2.INSTANCE, cliArguments, new ServiceLoaderWrapper());
        AppSettings settings = settingsLoader.load();
        // order is important - logging must be configured before any other components (AppFileSystem, ...)
        AppLogging logging = new AppLogging(settings);
        logging.configure();
        AppFileSystem fileSystem = new AppFileSystem(settings);

        try (AppState appState = new AppStateFactory(settings).create()) {
            appState.registerSonarQubeVersion(getSonarqubeVersion());
            appState.registerClusterName(settings.getProps().nonNullValue(CLUSTER_NAME.getKey()));
            AppReloader appReloader = new AppReloaderImpl(settingsLoader, fileSystem, appState, logging);
            fileSystem.reset();
            CommandFactory commandFactory = new CommandFactoryImpl(settings.getProps(), fileSystem.getTempDir(), System2.INSTANCE);

            try (ProcessLauncher processLauncher = new ProcessLauncherImpl(fileSystem.getTempDir())) {
                Scheduler scheduler = new CodeScanSchedulerImpl(settings, appReloader, commandFactory, processLauncher, appState);

                scheduler.schedule();

                stopRequestWatcher = StopRequestWatcherImpl.create(settings, scheduler, fileSystem);
                hardStopRequestWatcher = HardStopRequestWatcherImpl.create(scheduler, fileSystem);

                // intercepts CTRL-C
                Runtime.getRuntime().addShutdownHook(new ShutdownHook(scheduler));

                stopRequestWatcher.startWatching();
                hardStopRequestWatcher.startWatching();

                scheduler.awaitTermination();
                hardStopRequestWatcher.stopWatching();
            }
        } catch (Exception e) {
            Loggers.get(App.class).error("Startup failure", e);
        }

        systemExit.exit(0);
    }

    private void createDefaultSonarPropertiesIfRequired() throws IOException {
        File out = new File("conf/sonar.properties");
        if (!out.exists()) {
            Properties props = buildDefaultProperties(System.getenv(), System.getProperties());

            if (!out.getParentFile().exists()) {
                Files.createDirectories(out.getParentFile().toPath());
            }
            props.store(new FileOutputStream(out), "#default and overwritten properties from environment");
        }
    }

    static Properties buildDefaultProperties(Map<String, String> env, Properties systemProperties) throws IOException {
        Properties props = new Properties();
        props.load(App.class.getResourceAsStream("/default.sonar.properties"));
        // Applied before the prop.* and SONARQUBE_PROPERTIES entries below, so an explicit
        // prop.sonar.web.trustedProxiesRegex or SONARQUBE_PROPERTIES entry overwrites it when both are present.
        String trustedProxies = env.get(TRUSTED_PROXIES_ENV_VAR);
        if (trustedProxies != null && !trustedProxies.isBlank()) {
            props.put(TRUSTED_PROXIES_PROPERTY, trustedProxies.trim());
        }
        for (Entry<String, String> e : env.entrySet()) {
            if (e.getKey().startsWith("prop.")) {
                props.put(e.getKey().substring(5), e.getValue());
            }
        }
        for (Entry<java.lang.Object, java.lang.Object> e : systemProperties.entrySet()) {
            if (((String) e.getKey()).startsWith("prop.")) {
                props.put(((String) e.getKey()).substring(5), e.getValue());
            }
        }
        String sqProps = env.get("SONARQUBE_PROPERTIES");
        if (sqProps != null) {
            for (String prop : sqProps.split(";")) {
                if (prop.contains("=")) {
                    String[] keyValue = StringUtils.split(prop, "=", 2);
                    props.put(keyValue[0].trim(), keyValue[1].trim());
                }
            }
        }
        return props;
    }

    public static void main(String[] args) throws Exception {
        new App().start(args);
    }

    private class ShutdownHook extends Thread {
        private final Scheduler scheduler;

        public ShutdownHook(Scheduler scheduler) {
            super("Shutdown Hook");
            this.scheduler = scheduler;
        }

        @Override
        public void run() {
            systemExit.setInShutdownHook();
            stopRequestWatcher.stopWatching();
            hardStopRequestWatcher.stopWatching();

            // blocks until everything is corrected terminated
            scheduler.stop();
        }
    }
}
