package com.miempresa.fruver.service.usecase;

import com.miempresa.fruver.domain.exceptions.DomainException;
import com.miempresa.fruver.domain.model.DeviceConfig;
import com.miempresa.fruver.domain.repository.DeviceConfigRepository;

import java.util.Optional;

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
     *
     * Reglas:
     * - LECTOR puede tener puerto vacío (keyboard wedge).
     * - Si ya existe configuración para el tipo, se realiza un update (preserva id).
     */
    public DeviceConfig execute(String tipoStr, String puerto, String paramsJson) {
        if (tipoStr == null || tipoStr.isBlank()) {
            throw new DomainException("Tipo de dispositivo inválido");
        }

        final DeviceConfig.DeviceType tipo;
        try {
            tipo = DeviceConfig.DeviceType.valueOf(tipoStr.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new DomainException("Tipo desconocido: " + tipoStr);
        }

        // permitir puerto vacío sólo para LECTOR (modo keyboard)
        if (tipo != DeviceConfig.DeviceType.LECTOR) {
            if (puerto == null || puerto.isBlank()) {
                throw new DomainException("Puerto inválido");
            }
        } else {
            // normalizar null a empty string
            if (puerto == null) puerto = "";
        }

        String params = paramsJson == null ? "{}" : paramsJson;

        DeviceConfig cfg = new DeviceConfig(null, tipo, puerto, params);
        return repo.save(cfg);
    }
}
