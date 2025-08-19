
package com.miempresa.fruver.service.usecase;

import com.miempresa.fruver.domain.repository.DatabaseRepository;
import com.miempresa.fruver.service.dto.DatabaseStorageInfo;

import java.io.File;
import java.util.Optional;

public class GetDatabaseStorageUseCase {
    private final DatabaseRepository dbRepo;

    public GetDatabaseStorageUseCase(DatabaseRepository dbRepo) {
        this.dbRepo = dbRepo;
    }

    public DatabaseStorageInfo execute() {
        long used = dbRepo.getDatabaseUsedBytes();
        Optional<String> datadir = dbRepo.getDataDirPath();
        Optional<Long> fsFree = Optional.empty();
        if (datadir.isPresent()) {
            try {
                File dir = new File(datadir.get());
                if (dir.exists()) {
                    long free = dir.getFreeSpace();
                    fsFree = Optional.of(free);
                }
            } catch (Throwable t) {
                // ignore, no privileges o path inaccesible
            }
        }
        return new DatabaseStorageInfo(used, fsFree, datadir);
    }
}
