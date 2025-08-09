package com.miempresa.fruver.domain.repository;

import com.miempresa.fruver.domain.model.DeviceConfig;

import java.util.List;
import java.util.Optional;

public interface DeviceConfigRepository {
    DeviceConfig save(DeviceConfig d);
    Optional<DeviceConfig> findByType(DeviceConfig.DeviceType type);
    List<DeviceConfig> findAll();
}
