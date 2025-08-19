package com.miempresa.fruver.service.usecase;

import com.miempresa.fruver.domain.repository.DatabaseRepository;
import com.miempresa.fruver.service.port.DatabaseStorageInfo;

import java.io.File;
import java.util.Optional;

/**
 * Caso de uso para consultar el estado de almacenamiento de la BD.
 * Combina el tamaño real usado con la información del filesystem
 * (si se expone el datadir por permisos).
 */
public class GetDatabaseStorageUseCase {
    private final DatabaseRepository dbRepo;

    public GetDatabaseStorageUseCase(DatabaseRepository dbRepo) {
        this.dbRepo = dbRepo;
    }

    public DatabaseStorageInfo execute() {
        long used = dbRepo.getDatabaseUsedBytes();

        // ruta física del datadir (si el usuario MySQL tiene permiso para exponerlo)
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
                // ignorar (no privilegios o path inaccesible)
            }
        }

        return new DatabaseStorageInfo(used, fsFree, datadir);
    }
}
