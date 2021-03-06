// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.FileReference;
import com.yahoo.config.model.api.FileDistribution;
import com.yahoo.config.subscription.ConfigSourceSet;
import com.yahoo.io.IOUtils;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Transport;
import com.yahoo.net.HostName;
import com.yahoo.vespa.config.Connection;
import com.yahoo.vespa.config.ConnectionPool;
import com.yahoo.vespa.config.JRTConnectionPool;
import com.yahoo.vespa.config.server.ConfigServerSpec;
import com.yahoo.vespa.filedistribution.FileDownloader;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class FileServer {
    private static final Logger log = Logger.getLogger(FileServer.class.getName());
    private final FileDirectory root;
    private final ExecutorService executor;
    private final FileDownloader downloader;

    public static class ReplayStatus {
        private final int code;
        private final String description;
        public ReplayStatus(int code, String description) {
            this.code = code;
            this.description = description;
        }
        public boolean ok() { return code == 0; }
        public int getCode() { return code; }
        public String getDescription() { return description; }
    }

    public interface Receiver {
        void receive(FileReference reference, String filename, byte [] content, ReplayStatus status);
    }

    @Inject
    public FileServer(ConfigserverConfig configserverConfig) {
        this(createConnectionPool(configserverConfig), FileDistribution.getDefaultFileDBPath());
    }

    // For testing only
    public FileServer(File rootDir) {
        this(new EmptyConnectionPool(), rootDir);
    }

    private FileServer(ConnectionPool connectionPool, File rootDir) {
        this.downloader = new FileDownloader(connectionPool);
        this.root = new FileDirectory(rootDir);
        this.executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }

    public boolean hasFile(String fileName) {
        return hasFile(new FileReference(fileName));
    }
    public boolean hasFile(FileReference reference) {
        try {
            return root.getFile(reference).exists();
        } catch (IllegalArgumentException e) {
            log.warning("Failed locating file reference '" + reference + "' with error " + e.toString());
        }
        return false;
    }
    public boolean startFileServing(String fileName, Receiver target) {
        FileReference reference = new FileReference(fileName);
        File file = root.getFile(reference);

        if (file.exists()) {
            executor.execute(() -> serveFile(reference, target));
        }
        return false;
    }

    private void serveFile(FileReference reference, Receiver target) {
        File file = root.getFile(reference);
        // TODO remove once verified in system tests.
        log.info("Start serving reference '" + reference.value() + "' with file '" + file.getAbsolutePath() + "'");
        byte [] blob = new byte [0];
        boolean success = false;
        String errorDescription = "OK";
        try {
            blob = IOUtils.readFileBytes(file);
            success = true;
        } catch (IOException e) {
            errorDescription = "For file reference '" + reference.value() + "' I failed reading file '" + file.getAbsolutePath() + "'";
            log.warning(errorDescription + "for sending to '" + target.toString() + "'. " + e.toString());
        }
        target.receive(reference, file.getName(), blob,
                new ReplayStatus(success ? 0 : 1, success ? "OK" : errorDescription));
        // TODO remove once verified in system tests.
        log.info("Done serving reference '" + reference.toString() + "' with file '" + file.getAbsolutePath() + "'");
    }

    public void download(FileReference fileReference) {
        downloader.getFile(fileReference);
    }

    public FileDownloader downloader() {
        return downloader;
    }

    // Connection pool with all config servers except this one (might be an empty pool if there is only one config server)
    private static ConnectionPool createConnectionPool(ConfigserverConfig configserverConfig) {
        List<String> configServers = ConfigServerSpec.fromConfig(configserverConfig)
                .stream()
                .filter(spec -> !spec.getHostName().equals(HostName.getLocalhost()))
                .map(spec -> "tcp/" + spec.getHostName() + ":" + spec.getConfigServerPort())
                .collect(Collectors.toList());

        return configServers.size() > 0 ? new JRTConnectionPool(new ConfigSourceSet(configServers)) : new EmptyConnectionPool();
    }

    private static class EmptyConnectionPool implements ConnectionPool {

        @Override
        public void close() {}

        @Override
        public void setError(Connection connection, int i) {}

        @Override
        public Connection getCurrent() { return null; }

        @Override
        public Connection setNewCurrentConnection() { return null; }

        @Override
        public int getSize() { return 0; }

        @Override
        public Supervisor getSupervisor() { return new Supervisor(new Transport()); }
    }
}
