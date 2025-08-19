package com.miempresa.fruver.service.usecase;

import com.miempresa.fruver.domain.exceptions.DomainException;
import com.miempresa.fruver.domain.model.DeviceConfig;
import com.miempresa.fruver.domain.repository.DeviceConfigRepository;

/**
 * UseCase para guardar la configuración de un dispositivo (ej. BASCULA).
 * Convierte el tipo recibido como String al enum DeviceType y valida entrada.
 */
public class SaveDeviceConfigUseCase {

    private final DeviceConfigRepository repo;

    public SaveDeviceConfigUseCase(DeviceConfigRepository repo) {
        this.repo = repo;
    }

    /**
     * Guarda la configuración. `tipoStr` debe ser "BASCULA" | "IMPRESORA" | "LECTOR".
     */
    public DeviceConfig execute(String tipoStr, String puerto, String paramsJson) {
        if (tipoStr == null || tipoStr.isBlank()) {
            throw new DomainException("Tipo de dispositivo inválido");
        }
        if (puerto == null || puerto.isBlank()) {
            throw new DomainException("Puerto inválido");
        }

        final DeviceConfig.DeviceType tipo;
        try {
            tipo = DeviceConfig.DeviceType.valueOf(tipoStr.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new DomainException("Tipo desconocido: " + tipoStr);
        }

        String params = paramsJson == null ? "{}" : paramsJson;

        DeviceConfig cfg = new DeviceConfig(null, tipo, puerto, params);
        return repo.save(cfg);
    }
}
