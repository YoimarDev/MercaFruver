// fruver-domain/src/main/java/com/miempresa/fruver/domain/repository/DatabaseRepository.java
package com.miempresa.fruver.domain.repository;

import java.util.Optional;

public interface DatabaseRepository {
    /**
     * Retorna el total (bytes) usados por la BD actual (sum of data_length + index_length).
     */
    long getDatabaseUsedBytes();

    /**
     * Intenta retornar la ruta de datadir (p. ej. /var/lib/mysql) si está disponible.
     * Puede requerir privilegios pero normalmente SHOW VARIABLES devuelve la ruta.
     */
    Optional<String> getDataDirPath();
}
