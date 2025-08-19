// fruver-service/src/main/java/com/miempresa/fruver/service/dto/DatabaseStorageInfo.java
package com.miempresa.fruver.service.dto;

import java.util.Optional;

public class DatabaseStorageInfo {
    private final long usedBytes;
    private final Optional<Long> fsFreeBytes;
    private final Optional<String> dataDir;

    public DatabaseStorageInfo(long usedBytes, Optional<Long> fsFreeBytes, Optional<String> dataDir) {
        this.usedBytes = usedBytes;
        this.fsFreeBytes = fsFreeBytes;
        this.dataDir = dataDir;
    }

    public long getUsedBytes() { return usedBytes; }
    public Optional<Long> getFsFreeBytes() { return fsFreeBytes; }
    public Optional<String> getDataDir() { return dataDir; }
}
