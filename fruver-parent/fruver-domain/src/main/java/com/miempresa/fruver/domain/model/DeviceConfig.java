package com.miempresa.fruver.domain.model;

/**
 * Configuración de un dispositivo hardware.
 */
public class DeviceConfig {
    private Integer configId;
    private DeviceType tipo;
    private String puerto;
    private String parametrosJson;

    public enum DeviceType { BASCULA, IMPRESORA, LECTOR }

    public DeviceConfig(Integer configId, DeviceType tipo, String puerto, String parametrosJson) {
        this.configId = configId;
        this.tipo = tipo;
        this.puerto = puerto;
        this.parametrosJson = parametrosJson;
    }
    public Integer getId() { return configId; }
    public Integer getConfigId() { return configId; }
    public DeviceType getTipo() { return tipo; }
    public String getPuerto() { return puerto; }
    public String getParametrosJson() { return parametrosJson; }
}
